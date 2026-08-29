package com.nibash.community;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.resident.Resident;
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

/** {@code /api/events/} — CommitteeOrAdmin, with RSVP open to residents (spec §8.19). */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository events;
    private final EventAttendeeRepository attendees;
    private final ResidentRepository residents;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public EventController(EventRepository events, EventAttendeeRepository attendees,
                           ResidentRepository residents, BuildingRepository buildings, TenantService tenancy) {
        this.events = events;
        this.attendees = attendees;
        this.residents = residents;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record EventDto(Long id, Long building, String title, String description,
                           LocalDateTime eventDate, Long createdBy, String createdByName) {

        public static EventDto from(Event e) {
            return new EventDto(e.getId(), e.getBuilding().getId(), e.getTitle(), e.getDescription(),
                    e.getEventDate(), e.getCreatedBy().getId(), e.getCreatedBy().getName());
        }
    }

    public record AttendeeDto(Long id, Long event, Long resident, String status, String residentName) {

        public static AttendeeDto from(EventAttendee a) {
            return new AttendeeDto(a.getId(), a.getEvent().getId(), a.getResident().getId(),
                    a.getStatus(), a.getResident().getUser().getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<EventDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "eventDate"));
        return PageEnvelope.of(events.findByBuildingIdIn(scope, pageable), EventDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public EventDto detail(@PathVariable Long id) {
        return EventDto.from(scoped(id));
    }

    @GetMapping("/{id}/attendees/")
    @Transactional(readOnly = true)
    public Map<String, Object> attendees(@PathVariable Long id) {
        Event event = scoped(id);
        List<AttendeeDto> rows = attendees.findByEventIdOrderByIdAsc(event.getId()).stream()
                .map(AttendeeDto::from)
                .toList();
        return Map.of("event_id", event.getId(), "results", rows);
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<EventDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        Event event = new Event();
        event.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        event.setCreatedBy(caller);
        event.setTitle(Body.requireStr(body, "title"));
        event.setEventDate(Body.requireDateTime(body, "event_date"));
        event.setDescription(Body.str(body, "description"));
        return ResponseEntity.status(HttpStatus.CREATED).body(EventDto.from(events.save(event)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public EventDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Event event = scoped(id);
        if (body.containsKey("title")) {
            event.setTitle(Body.requireStr(body, "title"));
        }
        if (body.containsKey("event_date")) {
            event.setEventDate(Body.requireDateTime(body, "event_date"));
        }
        if (body.containsKey("description")) {
            event.setDescription(Body.str(body, "description"));
        }
        return EventDto.from(events.save(event));
    }

    @PutMapping("/{id}/")
    @Transactional
    public EventDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        events.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * RSVP (spec §8.19). Upserts rather than inserts: the table is unique per (event, resident), so
     * changing your mind updates the existing row instead of failing on the constraint.
     */
    @PostMapping("/{id}/rsvp/")
    @Transactional
    public AttendeeDto rsvp(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Event event = scoped(id);

        String status = Body.requireStr(body, "status");
        Body.requireOneOf(status, EventAttendee.STATUSES, "status");

        Resident resident = residents.findByUserIdAndBuildingId(caller.getId(), event.getBuilding().getId())
                .orElseThrow(() -> ApiException.badRequest("You are not a resident of this building."));

        EventAttendee attendee = attendees.findByEventIdAndResidentId(event.getId(), resident.getId())
                .orElseGet(() -> {
                    EventAttendee created = new EventAttendee();
                    created.setEvent(event);
                    created.setResident(resident);
                    return created;
                });
        attendee.setStatus(status);
        return AttendeeDto.from(attendees.save(attendee));
    }

    private Event scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return events.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
