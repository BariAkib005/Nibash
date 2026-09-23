package com.nibash.utility;

import com.nibash.activity.ActivityLogService;
import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/utility-bills/} — CommitteeOrAdmin (spec §8.15). Filters: {@code ?meter_id=},
 * {@code ?unit_id=}, {@code ?status=}.
 *
 * <p>Lifecycle: {@code pending} → {@code billed} once {@code generate-monthly} rolls the bill into a
 * resident's invoice → {@code paid} when that invoice is paid. Without the {@code billed} step a
 * pending bill would be added to every month's invoice.
 */
@RestController
@RequestMapping("/api/utility-bills")
public class UtilityBillController {

    private final UtilityBillRepository bills;
    private final UtilityMeterRepository meters;
    private final ActivityLogService activity;
    private final TenantService tenancy;

    public UtilityBillController(UtilityBillRepository bills, UtilityMeterRepository meters,
                                 ActivityLogService activity, TenantService tenancy) {
        this.bills = bills;
        this.meters = meters;
        this.activity = activity;
        this.tenancy = tenancy;
    }

    public record BillDto(Long id, Long meter, LocalDate readingDate, BigDecimal readingValue, BigDecimal amount,
                          String status, String meterNumber, String meterType, Long unit, String unitNumber) {

        public static BillDto from(UtilityBill b) {
            UtilityMeter m = b.getMeter();
            return new BillDto(b.getId(), m.getId(), b.getReadingDate(), b.getReadingValue(), b.getAmount(),
                    b.getStatus(), m.getMeterNumber(), m.getType(), m.getUnit().getId(), m.getUnit().getUnitNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<BillDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId,
                                      @RequestParam(name = "meter_id", required = false) Long meterId,
                                      @RequestParam(name = "unit_id", required = false) Long unitId,
                                      @RequestParam(required = false) String status) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "readingDate").and(Sort.by(Sort.Direction.DESC, "id")));
        String statusFilter = status == null || status.isBlank() ? null : status;
        return PageEnvelope.of(bills.search(scope, meterId, unitId, statusFilter, pageable), BillDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public BillDto detail(@PathVariable Long id) {
        return BillDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<BillDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        UtilityMeter meter = meters.findByIdAndUnitBuildingIdIn(Body.requireLong(body, "meter"), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        UtilityBill bill = new UtilityBill();
        bill.setMeter(meter);
        LocalDate readingDate = Body.asDate(body, "reading_date");
        if (readingDate == null) {
            throw ApiException.badRequest("reading_date is required");
        }
        bill.setReadingDate(readingDate);
        apply(bill, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(BillDto.from(bills.save(bill)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public BillDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        UtilityBill bill = scoped(id);
        if (!UtilityBill.PENDING.equals(bill.getStatus())
                && (body.containsKey("amount") || body.containsKey("reading_value"))) {
            throw ApiException.badRequest("This bill is already on an invoice, so its amount can't change.");
        }
        if (body.containsKey("reading_date")) {
            LocalDate readingDate = Body.asDate(body, "reading_date");
            if (readingDate == null) {
                throw ApiException.badRequest("reading_date is required");
            }
            bill.setReadingDate(readingDate);
        }
        apply(bill, body);
        return BillDto.from(bills.save(bill));
    }

    @PutMapping("/{id}/")
    @Transactional
    public BillDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        UtilityBill bill = scoped(id);
        if (!UtilityBill.PENDING.equals(bill.getStatus())) {
            throw ApiException.badRequest("This bill is already on an invoice, so it can't be deleted.");
        }
        bills.delete(bill);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * {@code POST /api/utility-bills/generate/} — one placeholder bill per meter in the building for
     * {@code month}, dated the 28th with zero reading and amount (spec §8.15; meter-reading
     * ingestion is future work). Re-running for the same month skips meters already billed, so,
     * like {@code generate-monthly}, pressing it twice creates nothing new. {@code rates_json} is
     * accepted for contract stability and not yet used.
     */
    @PostMapping("/generate/")
    @Transactional
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.asLong(body, "building_id");
        String month = Body.str(body, "month");
        if (buildingId == null || month == null || month.isBlank()) {
            throw ApiException.badRequest("building_id and month are required");
        }
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        tenancy.requireAccess(caller, buildingId);

        LocalDate readingDate = yearMonth.atDay(28);
        List<Long> created = new ArrayList<>();
        for (UtilityMeter meter : meters.findByBuilding(buildingId)) {
            if (bills.existsByMeterIdAndReadingDate(meter.getId(), readingDate)) {
                continue;
            }
            UtilityBill bill = new UtilityBill();
            bill.setMeter(meter);
            bill.setReadingDate(readingDate);
            bill.setReadingValue(BigDecimal.ZERO);
            bill.setAmount(BigDecimal.ZERO);
            bill.setStatus(UtilityBill.PENDING);
            created.add(bills.save(bill).getId());
        }
        if (!created.isEmpty()) {
            activity.record(caller, "building", buildingId, "generate_utility_bills",
                    Map.of("month", yearMonth.toString(), "created", created.size()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("created_bills", created));
    }

    private void apply(UtilityBill bill, Map<String, Object> body) {
        if (body.containsKey("reading_value")) {
            BigDecimal reading = Body.asDecimal(body, "reading_value");
            if (reading == null || reading.signum() < 0) {
                throw ApiException.badRequest("reading_value must be zero or more");
            }
            bill.setReadingValue(reading);
        }
        if (body.containsKey("amount")) {
            BigDecimal amount = Body.asDecimal(body, "amount");
            if (amount == null || amount.signum() < 0) {
                throw ApiException.badRequest("amount must be zero or more");
            }
            bill.setAmount(amount);
        }
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, UtilityBill.STATUSES, "status");
            bill.setStatus(status);
        }
    }

    private UtilityBill scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return bills.findByIdAndMeterUnitBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
