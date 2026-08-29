package com.nibash.community;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.community.EventController.AttendeeDto;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/event-attendees/} — IsAuthenticated (spec §8.19). {@code POST /api/events/{id}/rsvp/}
 * is the friendlier path; this resource is the plain CRUD the contract also specifies.
 */
@RestController
@RequestMapping("/api/event-attendees")
public class EventAttendeeController {

    private final EventAttendeeRepository attendees;
    private final EventRepository events;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public EventAttendeeController(EventAttendeeRepository attendees, EventRepository events,
                                   ResidentRepository residents, TenantService tenancy) {
        this.attendees = attendees;
        this.events = events;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AttendeeDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        return PageEnvelope.of(attendees.findByEventBuildingIdIn(scope, pageable), AttendeeDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AttendeeDto detail(@PathVariable Long id) {
        return AttendeeDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<AttendeeDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        List<Long> allowed = tenancy.allowedBuildingIds(caller);

        Event event = events.findByIdAndBuildingIdIn(Body.requireLong(body, "event"), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        Resident resident = residents.findById(Body.requireLong(body, "resident"))
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        String status = Body.str(body, "status");
        Body.requireOneOf(status, EventAttendee.STATUSES, "status");

        // Upsert, for the same reason the RSVP action does: the table is unique per pair.
        EventAttendee attendee = attendees.findByEventIdAndResidentId(event.getId(), resident.getId())
                .orElseGet(() -> {
                    EventAttendee created = new EventAttendee();
                    created.setEvent(event);
                    created.setResident(resident);
                    return created;
                });
        attendee.setStatus(status == null ? EventAttendee.INTERESTED : status);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttendeeDto.from(attendees.save(attendee)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public AttendeeDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        EventAttendee attendee = scoped(id);
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, EventAttendee.STATUSES, "status");
            attendee.setStatus(status);
        }
        return AttendeeDto.from(attendees.save(attendee));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attendees.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private EventAttendee scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return attendees.findByIdAndEventBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
