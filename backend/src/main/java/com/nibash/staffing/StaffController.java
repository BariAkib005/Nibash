package com.nibash.staffing;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.StaffDto;
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

/** {@code /api/staff/} — CommitteeOrAdmin (spec §8.5). Attendance arrives in Week 3. */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffRepository staff;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public StaffController(StaffRepository staff, BuildingRepository buildings, TenantService tenancy) {
        this.staff = staff;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<StaffDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(staff.findByBuildingIdIn(scope, pageable), StaffDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public StaffDto detail(@PathVariable Long id) {
        return StaffDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<StaffDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Long buildingId = body.get("building") == null ? null : Long.valueOf(String.valueOf(body.get("building")));
        if (buildingId == null) {
            throw ApiException.badRequest("building is required");
        }
        tenancy.requireAccess(caller, buildingId);
        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        Staff member = new Staff();
        member.setBuilding(building);
        member.setName(required(body, "name"));
        member.setRole(required(body, "role"));
        apply(member, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(StaffDto.from(staff.save(member)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public StaffDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Staff member = scoped(id);
        if (body.containsKey("name")) {
            member.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("role")) {
            member.setRole(String.valueOf(body.get("role")));
        }
        apply(member, body);
        return StaffDto.from(staff.save(member));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        staff.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Staff scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return staff.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private void apply(Staff member, Map<String, Object> body) {
        if (body.containsKey("designation")) {
            member.setDesignation(String.valueOf(body.get("designation")));
        }
        if (body.containsKey("qualifications")) {
            member.setQualifications(String.valueOf(body.get("qualifications")));
        }
        if (body.containsKey("contact_info")) {
            member.setContactInfo(String.valueOf(body.get("contact_info")));
        }
    }

    private static String required(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw ApiException.badRequest(key + " is required");
        }
        return value.toString();
    }
}
