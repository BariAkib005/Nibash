package com.nibash.visitor;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import com.nibash.visitor.VisitorDtos.VisitorDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/visitors/} — IsAuthenticated (spec §8.6). */
@RestController
@RequestMapping("/api/visitors")
public class VisitorController {

    private final VisitorRepository visitors;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final TenantService tenancy;

    public VisitorController(VisitorRepository visitors, AppointmentRepository appointments,
                             UserRepository users, TenantService tenancy) {
        this.visitors = visitors;
        this.appointments = appointments;
        this.users = users;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<VisitorDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "id"));
        return PageEnvelope.of(visitors.findByAppointmentBuildingIdIn(scope, pageable), VisitorDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public VisitorDto detail(@PathVariable Long id) {
        return VisitorDto.from(scoped(id));
    }

    /**
     * The gate scan (spec §8.6) — the one endpoint a guard actually uses, from a phone.
     *
     * <p>Three rules make it safe to hammer: the token is resolved <b>within the caller's
     * buildings</b>, so a token from another building is simply "not found"; a pass whose day has
     * passed is refused; and the visitor row is get-or-created with {@code checkin_time} stamped
     * only once, so re-scanning the same pass is idempotent rather than rewriting the arrival time.
     */
    @PostMapping("/scan/")
    @Transactional
    public VisitorDto scan(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        String token = Body.str(body, "qr_token");
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("qr_token is required");
        }

        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        Appointment appointment = appointments.findByQrTokenAndBuildingIdIn(token.trim(), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        // Date, not instant: a visitor expected at 14:00 who turns up at 19:00 still gets in today.
        if (appointment.getScheduledTime().toLocalDate().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("Appointment has expired");
        }

        Visitor visitor = visitors.findFirstByAppointmentIdOrderByIdAsc(appointment.getId())
                .orElseGet(() -> {
                    Visitor created = new Visitor();
                    created.setAppointment(appointment);
                    return created;
                });

        visitor.setStatus(Visitor.CHECKED_IN);
        if (visitor.getCheckinTime() == null) {
            visitor.setCheckinTime(Times.now());
        }
        resolveHandler(body, caller, false).ifPresent(visitor::setHandledBy);

        return VisitorDto.from(visitors.save(visitor));
    }

    @PatchMapping("/{id}/checkin/")
    @Transactional
    public VisitorDto checkin(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Visitor visitor = scoped(id);
        visitor.setStatus(Visitor.CHECKED_IN);
        visitor.setCheckinTime(Times.now());
        stampHandler(visitor, body);
        return VisitorDto.from(visitors.save(visitor));
    }

    @PatchMapping("/{id}/checkout/")
    @Transactional
    public VisitorDto checkout(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Visitor visitor = scoped(id);
        visitor.setStatus(Visitor.CHECKED_OUT);
        visitor.setCheckoutTime(Times.now());
        stampHandler(visitor, body);
        return VisitorDto.from(visitors.save(visitor));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        visitors.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** On check-in/out the caller is the handler unless the body names someone else (spec §8.6). */
    private void stampHandler(Visitor visitor, Map<String, Object> body) {
        User caller = CurrentUser.require();
        resolveHandler(body == null ? Map.of() : body, caller, true).ifPresent(visitor::setHandledBy);
    }

    private java.util.Optional<User> resolveHandler(Map<String, Object> body, User caller, boolean defaultToCaller) {
        Long handledBy = Body.asLong(body, "handled_by");
        if (handledBy != null) {
            return java.util.Optional.of(users.findById(handledBy)
                    .orElseThrow(() -> ApiException.notFound("Not found.")));
        }
        return defaultToCaller ? java.util.Optional.of(caller) : java.util.Optional.empty();
    }

    private Visitor scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return visitors.findByIdAndAppointmentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
