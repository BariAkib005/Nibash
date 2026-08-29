package com.nibash.visitor;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.visitor.VisitorDtos.AppointmentDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/appointments/} — IsAuthenticated (spec §8.6). */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentRepository appointments;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public AppointmentController(AppointmentRepository appointments, ResidentRepository residents,
                                 TenantService tenancy) {
        this.appointments = appointments;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AppointmentDto> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "scheduledTime"));
        return PageEnvelope.of(appointments.findByBuildingIdIn(scope, pageable), AppointmentDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AppointmentDto detail(@PathVariable Long id) {
        return AppointmentDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<AppointmentDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();

        Long residentId = Body.requireLong(body, "resident");
        Resident resident = residents.findById(residentId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        Appointment appointment = new Appointment();
        appointment.setResident(resident);
        appointment.setBuilding(resident.getBuilding());
        appointment.setVisitorName(Body.requireStr(body, "visitor_name"));
        appointment.setScheduledTime(Body.requireDateTime(body, "scheduled_time"));
        apply(appointment, body);

        // The token is the pass: without one there is nothing for the gate to scan.
        if (appointment.getQrToken() == null || appointment.getQrToken().isBlank()) {
            appointment.setQrToken(newToken());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppointmentDto.from(appointments.save(appointment)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public AppointmentDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Appointment appointment = scoped(id);
        if (body.containsKey("visitor_name")) {
            appointment.setVisitorName(Body.requireStr(body, "visitor_name"));
        }
        if (body.containsKey("scheduled_time")) {
            appointment.setScheduledTime(Body.requireDateTime(body, "scheduled_time"));
        }
        apply(appointment, body);
        return AppointmentDto.from(appointments.save(appointment));
    }

    @PutMapping("/{id}/")
    @Transactional
    public AppointmentDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        appointments.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** What the resident's phone renders as a QR code (spec §8.6). */
    @GetMapping("/{id}/qr/")
    @Transactional(readOnly = true)
    public Map<String, Object> qr(@PathVariable Long id) {
        Appointment appointment = scoped(id);
        if (appointment.getQrToken() == null) {
            appointment.setQrToken(newToken());
            appointments.save(appointment);
        }
        return Map.of("appointment_id", appointment.getId(), "qr_token", appointment.getQrToken());
    }

    private void apply(Appointment appointment, Map<String, Object> body) {
        if (body.containsKey("visitor_phone")) {
            String phone = Body.str(body, "visitor_phone");
            appointment.setVisitorPhone(phone == null ? "" : phone.trim());
        }
        if (body.containsKey("approved")) {
            appointment.setApproved(Body.asBool(body, "approved"));
        }
        if (body.containsKey("qr_token")) {
            String token = Body.str(body, "qr_token");
            appointment.setQrToken(token == null || token.isBlank() ? null : token.trim());
        }
    }

    /** 32 hex characters (spec §8.6), retried on the vanishingly rare collision. */
    private String newToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = UUID.randomUUID().toString().replace("-", "");
            if (!appointments.existsByQrToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique QR token");
    }

    private Appointment scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return appointments.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
