package com.nibash.activity;

import com.nibash.auth.CurrentUser;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.StaffRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code GET /api/activity-logs/} — read-only, CommitteeOrAdminStrict (spec §8.23 leaves the route
 * optional; it is wired here for the activity screen). Filter: {@code ?entity_type=}.
 *
 * <p>The table has no building column, so a building's managers see the actions of the people
 * attached to their buildings — the same set user administration shows them. Back-office sees all.
 */
@RestController
@RequestMapping("/api/activity-logs")
public class ActivityLogController {

    private final ActivityLogRepository logs;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final UserRepository users;
    private final TenantService tenancy;

    public ActivityLogController(ActivityLogRepository logs, ResidentRepository residents, StaffRepository staff,
                                 UserRepository users, TenantService tenancy) {
        this.logs = logs;
        this.residents = residents;
        this.staff = staff;
        this.users = users;
        this.tenancy = tenancy;
    }

    public record ActivityDto(Long id, Long user, String entityType, Integer entityId, String action,
                              String detailsJson, LocalDateTime timestamp, String userName) {

        public static ActivityDto from(ActivityLog a) {
            return new ActivityDto(a.getId(), a.getUser().getId(), a.getEntityType(), a.getEntityId(), a.getAction(),
                    a.getDetailsJson(), a.getTimestamp(), a.getUser().getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ActivityDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId,
                                          @RequestParam(name = "entity_type", required = false) String entityType) {
        Policy.requireStrict();
        User caller = CurrentUser.require();
        boolean all = caller.isBackOffice() && buildingId == null;

        Set<Long> userIds = new LinkedHashSet<>();
        if (!all) {
            List<Long> scope = tenancy.resolveScope(caller, buildingId);
            if (scope.isEmpty()) {
                return new PageEnvelope<>(0, null, null, List.of());
            }
            userIds.addAll(residents.findUserIdsInBuildings(scope));
            userIds.addAll(staff.findUserIdsInBuildings(scope));
            userIds.addAll(users.findDeveloperIds(scope));
            userIds.addAll(users.findPrimaryContactIds(scope));
            if (userIds.isEmpty()) {
                return new PageEnvelope<>(0, null, null, List.of());
            }
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id")));
        String type = entityType == null || entityType.isBlank() ? null : entityType.trim();
        return PageEnvelope.of(logs.search(all, all ? List.of(-1L) : userIds, type, pageable), ActivityDto::from);
    }
}
