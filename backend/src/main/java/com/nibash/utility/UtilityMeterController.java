package com.nibash.utility;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/utility-meters/} — CommitteeOrAdmin (spec §8.15). Filter: {@code ?unit_id=}. */
@RestController
@RequestMapping("/api/utility-meters")
public class UtilityMeterController {

    private final UtilityMeterRepository meters;
    private final UtilityBillRepository bills;
    private final UnitRepository units;
    private final TenantService tenancy;

    public UtilityMeterController(UtilityMeterRepository meters, UtilityBillRepository bills, UnitRepository units,
                                  TenantService tenancy) {
        this.meters = meters;
        this.bills = bills;
        this.units = units;
        this.tenancy = tenancy;
    }

    public record MeterDto(Long id, Long unit, String type, String meterNumber, String unitNumber) {

        public static MeterDto from(UtilityMeter m) {
            return new MeterDto(m.getId(), m.getUnit().getId(), m.getType(), m.getMeterNumber(),
                    m.getUnit().getUnitNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<MeterDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId,
                                       @RequestParam(name = "unit_id", required = false) Long unitId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by("unit.unitNumber").and(Sort.by("type")));
        var rows = unitId == null
                ? meters.findByUnitBuildingIdIn(scope, pageable)
                : meters.findByUnitBuildingIdInAndUnitId(scope, unitId, pageable);
        return PageEnvelope.of(rows, MeterDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public MeterDto detail(@PathVariable Long id) {
        return MeterDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<MeterDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        Unit unit = units.findByIdAndBuildingIdIn(Body.requireLong(body, "unit"), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        String type = Body.requireStr(body, "type");
        Body.requireOneOf(type, UtilityMeter.TYPES, "type");
        if (meters.findByUnitIdAndType(unit.getId(), type).isPresent()) {
            throw ApiException.badRequest("Unit " + unit.getUnitNumber() + " already has a " + type + " meter.");
        }
        String number = Body.requireStr(body, "meter_number");
        requireUniqueNumber(number, null);

        UtilityMeter meter = new UtilityMeter();
        meter.setUnit(unit);
        meter.setType(type);
        meter.setMeterNumber(number);
        return ResponseEntity.status(HttpStatus.CREATED).body(MeterDto.from(meters.save(meter)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public MeterDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        UtilityMeter meter = scoped(id);
        if (body.containsKey("meter_number")) {
            String number = Body.requireStr(body, "meter_number");
            requireUniqueNumber(number, meter.getId());
            meter.setMeterNumber(number);
        }
        if (body.containsKey("type")) {
            String type = Body.requireStr(body, "type");
            Body.requireOneOf(type, UtilityMeter.TYPES, "type");
            meters.findByUnitIdAndType(meter.getUnit().getId(), type)
                    .filter(other -> !other.getId().equals(meter.getId()))
                    .ifPresent(other -> {
                        throw ApiException.badRequest("This unit already has a " + type + " meter.");
                    });
            meter.setType(type);
        }
        return MeterDto.from(meters.save(meter));
    }

    @PutMapping("/{id}/")
    @Transactional
    public MeterDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        UtilityMeter meter = scoped(id);
        if (bills.existsByMeterId(meter.getId())) {
            throw ApiException.badRequest("This meter has bills, so it can't be deleted.");
        }
        meters.delete(meter);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void requireUniqueNumber(String number, Long selfId) {
        meters.findByMeterNumberIgnoreCase(number)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw ApiException.badRequest("A meter with this number already exists.");
                });
    }

    private UtilityMeter scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return meters.findByIdAndUnitBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
