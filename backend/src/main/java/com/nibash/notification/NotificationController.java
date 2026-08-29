package com.nibash.notification;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/notifications/} — IsAuthenticated (spec §8.18). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;
    private final BuildingRepository buildings;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public NotificationController(NotificationRepository notifications, BuildingRepository buildings,
                                  ResidentRepository residents, TenantService tenancy) {
        this.notifications = notifications;
        this.buildings = buildings;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record NotificationDto(Long id, Long building, Long resident, String type,
                                  String message, LocalDateTime sentAt, boolean isRead) {

        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getBuilding().getId(),
                    n.getResident() == null ? null : n.getResident().getId(),
                    n.getType(), n.getMessage(), n.getSentAt(), n.isRead());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<NotificationDto> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "sentAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(notifications.findByBuildingIdIn(scope, pageable), NotificationDto::from);
    }

    /** The bell's badge — a count, so the dropdown does not have to be open to stay current. */
    @GetMapping("/unread-count/")
    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        long unread = scope.isEmpty() ? 0 : notifications.countByBuildingIdInAndReadFalse(scope);
        return Map.of("unread", unread);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public NotificationDto detail(@PathVariable Long id) {
        return NotificationDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<NotificationDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        Notification notification = new Notification();
        notification.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        notification.setType(Body.requireStr(body, "type"));
        notification.setMessage(Body.requireStr(body, "message"));
        notification.setSentAt(Times.now());
        apply(notification, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationDto.from(notifications.save(notification)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public NotificationDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Notification notification = scoped(id);
        if (body.containsKey("message")) {
            notification.setMessage(Body.requireStr(body, "message"));
        }
        apply(notification, body);
        return NotificationDto.from(notifications.save(notification));
    }

    @PutMapping("/{id}/")
    @Transactional
    public NotificationDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notifications.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(Notification notification, Map<String, Object> body) {
        if (body.containsKey("is_read")) {
            notification.setRead(Body.asBool(body, "is_read"));
        }
        if (body.containsKey("resident")) {
            Long residentId = Body.asLong(body, "resident");
            notification.setResident(residentId == null ? null
                    : residents.findById(residentId).orElseThrow(() -> ApiException.notFound("Not found.")));
        }
    }

    private Notification scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return notifications.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
