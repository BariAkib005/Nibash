package com.nibash.building;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.BuildingDto;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/buildings/} — CommitteeOrAdmin (spec §8.1). List = the caller's allowed buildings. */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public BuildingController(BuildingRepository buildings, TenantService tenancy) {
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<BuildingDto> list(@RequestParam(defaultValue = "1") int page) {
        User caller = CurrentUser.require();
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        if (allowed.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(buildings.findByIdIn(allowed, pageable), BuildingDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public BuildingDto detail(@PathVariable Long id) {
        return BuildingDto.from(scoped(id));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public BuildingDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Building building = scoped(id);
        applyUpdates(building, body);
        return BuildingDto.from(buildings.save(building));
    }

    @PutMapping("/{id}/")
    @Transactional
    public BuildingDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        buildings.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Out-of-tenant IDs return 404, never 403 (spec §6.3). */
    private Building scoped(Long id) {
        User caller = CurrentUser.require();
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        return buildings.findByIdAndIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    /** Shared with the settings endpoint's {@code building} section (spec §4.3). */
    public static void applyUpdates(Building building, Map<String, Object> body) {
        if (body.containsKey("name")) {
            building.setName(str(body.get("name")));
        }
        if (body.containsKey("address")) {
            building.setAddress(str(body.get("address")));
        }
        if (body.containsKey("website")) {
            building.setWebsite(str(body.get("website")));
        }
        if (body.containsKey("num_floors")) {
            building.setNumFloors(intOrNull(body.get("num_floors")));
        }
        if (body.containsKey("total_units")) {
            building.setTotalUnits(intOrNull(body.get("total_units")));
        }
        if (body.containsKey("year_built")) {
            Integer year = intOrNull(body.get("year_built"));
            building.setYearBuilt(year == null ? null : year.shortValue());
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intOrNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Expected a number, got \"" + value + "\".");
        }
    }
}
