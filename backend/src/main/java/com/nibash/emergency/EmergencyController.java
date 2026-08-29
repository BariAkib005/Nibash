package com.nibash.emergency;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.notification.Notification;
import com.nibash.notification.NotificationRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/emergencies/} — IsAuthenticated (spec §8.11). */
@RestController
@RequestMapping("/api/emergencies")
public class EmergencyController {

    /** Spec §8.11 quotes this text, so it is part of the contract. */
    private static final String SOS_MESSAGE = "Emergency SOS alert created. Security has been notified.";

    private final EmergencyRepository emergencies;
    private final ResidentRepository residents;
    private final NotificationRepository notifications;
    private final TenantService tenancy;

    public EmergencyController(EmergencyRepository emergencies, ResidentRepository residents,
                               NotificationRepository notifications, TenantService tenancy) {
        this.emergencies = emergencies;
        this.residents = residents;
        this.notifications = notifications;
        this.tenancy = tenancy;
    }

    public record EmergencyDto(Long id, Long building, Long resident, BigDecimal latitude,
                               BigDecimal longitude, LocalDateTime timestamp,
                               String residentName, String unitNumber) {

        public static EmergencyDto from(Emergency e) {
            var resident = e.getResident();
            var unit = resident.getUnit();
            return new EmergencyDto(e.getId(), e.getBuilding().getId(), resident.getId(),
                    e.getLatitude(), e.getLongitude(), e.getTimestamp(),
                    resident.getUser().getName(), unit == null ? null : unit.getUnitNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<EmergencyDto> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        return PageEnvelope.of(emergencies.findByBuildingIdIn(scope, pageable), EmergencyDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public EmergencyDto detail(@PathVariable Long id) {
        return EmergencyDto.from(scoped(id));
    }

    /**
     * Raise an SOS (spec §8.11).
     *
     * <p>The notification row is written in the <b>same transaction</b> as the alert: an SOS that
     * nobody is told about is worse than no SOS at all, so the two either both land or neither does.
     */
    @PostMapping("/")
    @Transactional
    public ResponseEntity<EmergencyDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();

        Long residentId = Body.requireLong(body, "resident");
        Resident resident = residents.findById(residentId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        Emergency emergency = new Emergency();
        emergency.setResident(resident);
        emergency.setBuilding(resident.getBuilding());
        emergency.setLatitude(Body.asDecimal(body, "latitude"));
        emergency.setLongitude(Body.asDecimal(body, "longitude"));
        emergency.setTimestamp(Times.now());
        Emergency saved = emergencies.save(emergency);

        Notification alert = new Notification();
        alert.setBuilding(resident.getBuilding());
        alert.setResident(resident);
        alert.setType(Notification.SOS);
        alert.setMessage(SOS_MESSAGE);
        alert.setSentAt(Times.now());
        notifications.save(alert);

        return ResponseEntity.status(HttpStatus.CREATED).body(EmergencyDto.from(saved));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        emergencies.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Emergency scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return emergencies.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
