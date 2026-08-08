package com.nibash.user;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.UserDto;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.StaffRepository;
import com.nibash.tenancy.TenantService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/users/} — <b>strict</b> admin/committee: even reads require the role (spec §8.1).
 * Scope = users attached to the caller's buildings as a resident, as staff, or as the building's
 * developer / primary contact.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final TenantService tenancy;

    public UserController(UserRepository users, ResidentRepository residents,
                          StaffRepository staff, TenantService tenancy) {
        this.users = users;
        this.residents = residents;
        this.staff = staff;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<UserDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId) {
        Policy.requireStrict();
        List<Long> visible = visibleUserIds(buildingId);
        if (visible.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(users.findByIdIn(visible, pageable), UserDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public UserDto detail(@PathVariable Long id) {
        Policy.requireStrict();
        return UserDto.from(scoped(id));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public UserDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireStrict();
        User user = scoped(id);

        if (body.containsKey("name")) {
            user.setName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("phone")) {
            user.setPhone(String.valueOf(body.get("phone")));
        }
        if (body.containsKey("role")) {
            String role = String.valueOf(body.get("role"));
            if (!Roles.isValid(role)) {
                throw ApiException.badRequest("role must be one of " + Roles.ALL);
            }
            user.setRole(role);
        }
        if (body.containsKey("is_listed")) {
            user.setListed(Boolean.parseBoolean(String.valueOf(body.get("is_listed"))));
        }
        // password_hash / is_staff / is_superuser are deliberately not editable through this endpoint.
        return UserDto.from(users.save(user));
    }

    private User scoped(Long id) {
        List<Long> visible = visibleUserIds(null);
        return users.findByIdAndIdIn(id, visible)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    /** Users reachable from the caller's buildings through any of the four membership paths. */
    private List<Long> visibleUserIds(Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(residents.findUserIdsInBuildings(scope));
        ids.addAll(staff.findUserIdsInBuildings(scope));
        ids.addAll(users.findDeveloperIds(scope));
        ids.addAll(users.findPrimaryContactIds(scope));
        return List.copyOf(ids);
    }
}
