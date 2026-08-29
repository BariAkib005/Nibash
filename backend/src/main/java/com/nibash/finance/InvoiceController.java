package com.nibash.finance;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.finance.FinanceDtos.InvoiceDto;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.utility.UtilityBill;
import com.nibash.utility.UtilityBillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/invoices/} — CommitteeOrAdmin. Filters per spec §8.3. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    /** The flat monthly service charge the batch raises for every resident (spec §8.3). */
    private static final BigDecimal SERVICE_CHARGE = new BigDecimal("2000.00");

    private final InvoiceRepository invoices;
    private final BillTypeRepository billTypes;
    private final ResidentRepository residents;
    private final BuildingRepository buildings;
    private final UtilityBillRepository utilityBills;
    private final TenantService tenancy;

    public InvoiceController(InvoiceRepository invoices, BillTypeRepository billTypes,
                             ResidentRepository residents, BuildingRepository buildings,
                             UtilityBillRepository utilityBills, TenantService tenancy) {
        this.invoices = invoices;
        this.billTypes = billTypes;
        this.residents = residents;
        this.buildings = buildings;
        this.utilityBills = utilityBills;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<InvoiceDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "resident_id", required = false) Long residentId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(name = "due_before", required = false) String dueBefore,
                                         @RequestParam(name = "due_after", required = false) String dueAfter) {

        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        var result = invoices.search(scope, residentId, blankToNull(status),
                parseDate(dueBefore, "due_before"), parseDate(dueAfter, "due_after"), pageable);
        return PageEnvelope.of(result, InvoiceDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public InvoiceDto detail(@PathVariable Long id) {
        return InvoiceDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<InvoiceDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Long residentId = Body.requireLong(body, "resident");
        Resident resident = residents.findById(residentId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        Invoice invoice = new Invoice();
        invoice.setResident(resident);
        invoice.setBuilding(resident.getBuilding());
        invoice.setInvoiceNumber(Body.requireStr(body, "invoice_number"));
        invoice.setDueDate(requireDate(body, "due_date"));
        apply(invoice, body);

        for (Map<String, Object> item : Body.objectList(body, "items")) {
            invoice.addItem(toItem(item));
        }
        // The header total is always the sum of the lines, never a client-supplied number.
        if (!invoice.getItems().isEmpty()) {
            invoice.setAmount(sumItems(invoice));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceDto.from(invoices.save(invoice)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public InvoiceDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Invoice invoice = scoped(id);
        if (body.containsKey("due_date")) {
            invoice.setDueDate(requireDate(body, "due_date"));
        }
        apply(invoice, body);
        return InvoiceDto.from(invoices.save(invoice));
    }

    @PutMapping("/{id}/")
    @Transactional
    public InvoiceDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        invoices.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * The monthly service-charge batch (spec §8.3).
     *
     * <p><b>Idempotent by construction:</b> the invoice number is derived from the resident and the
     * billing month, so a re-run finds every row already present and creates nothing. That matters
     * because this is the one button an admin is most likely to press twice.
     */
    @PostMapping("/generate-monthly/")
    @Transactional
    public ResponseEntity<Map<String, Object>> generateMonthly(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Long buildingId = Body.requireLong(body, "building_id");
        Long billTypeId = Body.requireLong(body, "bill_type_id");
        YearMonth billingMonth = parseMonth(Body.requireStr(body, "billing_month"));
        LocalDate dueDate = requireDate(body, "due_date");
        boolean includeUtilities = Body.asBool(body, "include_utilities");

        // Deliberately 403 with its own message here, unlike the usual 404 — the caller named a
        // building explicitly and the contract quotes this text (spec §8.3).
        if (!tenancy.canAccess(caller, buildingId)) {
            throw ApiException.forbidden("Building not accessible.");
        }
        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        BillType billType = billTypes.findById(billTypeId)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        String monthSuffix = "%d%02d".formatted(billingMonth.getYear(), billingMonth.getMonthValue());
        List<Long> created = new ArrayList<>();

        for (Resident resident : residents.findByBuildingIdOrderByIdAsc(buildingId)) {
            String number = "AUTO-%d-%s".formatted(resident.getId(), monthSuffix);
            if (invoices.findByInvoiceNumber(number).isPresent()) {
                continue;
            }

            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(number);
            invoice.setResident(resident);
            invoice.setBuilding(building);
            invoice.setBillType(billType);
            invoice.setDueDate(dueDate);
            invoice.setStatus(Invoice.PENDING);
            invoice.addItem(serviceChargeItem());

            if (includeUtilities && resident.getUnit() != null) {
                for (UtilityBill bill : utilityBills.findPendingForUnit(resident.getUnit().getId())) {
                    invoice.addItem(utilityItem(bill));
                }
            }
            invoice.setAmount(sumItems(invoice));
            created.add(invoices.save(invoice).getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("created_invoices", created));
    }

    /** Stub hook for the reminder channel (spec §8.3); the scheduled job is the real sender. */
    @PostMapping("/{id}/remind/")
    @Transactional(readOnly = true)
    public Map<String, Object> remind(@PathVariable Long id) {
        Policy.requireManager();
        Invoice invoice = scoped(id);
        return Map.of("detail", "Reminder queued for invoice " + invoice.getInvoiceNumber());
    }

    private InvoiceItem serviceChargeItem() {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Monthly Service Charge");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(SERVICE_CHARGE);
        item.setTotalAmount(SERVICE_CHARGE);
        return item;
    }

    private InvoiceItem utilityItem(UtilityBill bill) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription("Utility %s %s".formatted(bill.getMeter().getType(), bill.getReadingDate()));
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(bill.getAmount());
        item.setTotalAmount(bill.getAmount());
        item.setUtilityBillId(bill.getId().intValue());
        return item;
    }

    private static BigDecimal sumItems(Invoice invoice) {
        return invoice.getItems().stream()
                .map(InvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private InvoiceItem toItem(Map<String, Object> body) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription(Body.requireStr(body, "description"));
        BigDecimal quantity = Body.asDecimal(body, "quantity");
        BigDecimal unitPrice = Body.asDecimal(body, "unit_price");
        BigDecimal tax = Body.asDecimal(body, "tax_amount");
        BigDecimal total = Body.asDecimal(body, "total_amount");

        item.setQuantity(quantity == null ? BigDecimal.ONE : quantity);
        item.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
        item.setTaxAmount(tax == null ? BigDecimal.ZERO : tax);
        // A client that omits the line total gets the obvious arithmetic rather than a zero row.
        item.setTotalAmount(total != null ? total
                : item.getQuantity().multiply(item.getUnitPrice()).add(item.getTaxAmount()));
        item.setUtilityBillId(Body.asInt(body, "utility_bill_id"));
        return item;
    }

    private void apply(Invoice invoice, Map<String, Object> body) {
        if (body.containsKey("bill_type")) {
            Long billTypeId = Body.asLong(body, "bill_type");
            invoice.setBillType(billTypeId == null ? null
                    : billTypes.findById(billTypeId).orElseThrow(() -> ApiException.notFound("Not found.")));
        }
        if (body.containsKey("amount")) {
            invoice.setAmount(Body.asDecimal(body, "amount"));
        }
        if (body.containsKey("status")) {
            String status = Body.str(body, "status");
            Body.requireOneOf(status, Invoice.STATUSES, "status");
            invoice.setStatus(status);
        }
    }

    private Invoice scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return invoices.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(field + " must be a date (YYYY-MM-DD)");
        }
    }

    private static LocalDate requireDate(Map<String, Object> body, String key) {
        LocalDate value = Body.asDate(body, key);
        if (value == null) {
            throw ApiException.badRequest(key + " is required");
        }
        return value;
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("billing_month must be YYYY-MM");
        }
    }
}
