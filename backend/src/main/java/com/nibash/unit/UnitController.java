package com.nibash.unit;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.UnitDto;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/units/} — CommitteeOrAdmin. Filters: {@code ?status=}, {@code ?building_id=} (spec §8.1). */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private static final List<String> STATUSES = List.of("available", "occupied", "sold", "rented");
    private static final List<String> TYPES =
            List.of("studio", "1BHK", "2BHK", "3BHK", "duplex", "shop", "office");

    private final UnitRepository units;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public UnitController(UnitRepository units, BuildingRepository buildings, TenantService tenancy) {
        this.units = units;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<UnitDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId,
                                      @RequestParam(required = false) String status) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by("building.id").and(Sort.by("unitNumber")));

        var result = status == null || status.isBlank()
                ? units.findByBuildingIdIn(scope, pageable)
                : units.findByBuildingIdInAndStatus(scope, status, pageable);
        return PageEnvelope.of(result, UnitDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public UnitDto detail(@PathVariable Long id) {
        return UnitDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<UnitDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Long buildingId = asLong(body.get("building"));
        if (buildingId == null) {
            throw ApiException.badRequest("building is required");
        }
        tenancy.requireAccess(caller, buildingId);
        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        String unitNumber = str(body.get("unit_number"));
        if (unitNumber == null || unitNumber.isBlank()) {
            throw ApiException.badRequest("unit_number is required");
        }
        units.findByBuildingIdAndUnitNumber(buildingId, unitNumber).ifPresent(existing -> {
            throw ApiException.badRequest("A unit with this number already exists in the building.");
        });

        Unit unit = new Unit();
        unit.setBuilding(building);
        unit.setUnitNumber(unitNumber);
        apply(unit, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(UnitDto.from(units.save(unit)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public UnitDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Unit unit = scoped(id);
        if (body.containsKey("unit_number")) {
            unit.setUnitNumber(str(body.get("unit_number")));
        }
        apply(unit, body);
        return UnitDto.from(units.save(unit));
    }

    @PutMapping("/{id}/")
    @Transactional
    public UnitDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        units.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Unit scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return units.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private void apply(Unit unit, Map<String, Object> body) {
        if (body.containsKey("floor")) {
            unit.setFloor(asInt(body.get("floor")));
        }
        if (body.containsKey("type")) {
            String type = str(body.get("type"));
            if (type != null && !TYPES.contains(type)) {
                throw ApiException.badRequest("type must be one of " + TYPES);
            }
            unit.setType(type);
        }
        if (body.containsKey("size_sqft")) {
            unit.setSizeSqft(asDecimal(body.get("size_sqft")));
        }
        if (body.containsKey("price")) {
            unit.setPrice(asDecimal(body.get("price")));
        }
        if (body.containsKey("status")) {
            String status = str(body.get("status"));
            if (status != null && !STATUSES.contains(status)) {
                throw ApiException.badRequest("status must be one of " + STATUSES);
            }
            unit.setStatus(status);
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long asLong(Object v) {
        return v == null ? null : Long.valueOf(v.toString().trim());
    }

    private static Integer asInt(Object v) {
        return v == null || v.toString().isBlank() ? null : Integer.valueOf(v.toString().trim());
    }

    private static BigDecimal asDecimal(Object v) {
        return v == null || v.toString().isBlank() ? null : new BigDecimal(v.toString().trim());
    }
}
