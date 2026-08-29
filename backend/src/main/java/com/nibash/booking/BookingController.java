package com.nibash.booking;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.FieldException;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/bookings/} — IsAuthenticated (spec §8.8). */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    /** Both messages are quoted in spec §8.8 and matched by the calendar's inline validation. */
    private static final String END_BEFORE_START = "End time must be after start time.";
    private static final String ALREADY_BOOKED = "This resource already has a booking in that time window.";

    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public BookingController(BookingRepository bookings, ResourceRepository resources,
                             ResidentRepository residents, TenantService tenancy) {
        this.bookings = bookings;
        this.resources = resources;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record BookingDto(Long id, Long resource, Long resident, LocalDateTime startTime,
                             LocalDateTime endTime, String status, String purpose,
                             LocalDateTime createdAt, String resourceName, String residentName) {

        public static BookingDto from(Booking b) {
            return new BookingDto(b.getId(), b.getResource().getId(), b.getResident().getId(),
                    b.getStartTime(), b.getEndTime(), b.getStatus(), b.getPurpose(), b.getCreatedAt(),
                    b.getResource().getName(), b.getResident().getUser().getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<BookingDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "startTime"));
        return PageEnvelope.of(bookings.findByResourceBuildingIdIn(scope, pageable), BookingDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public BookingDto detail(@PathVariable Long id) {
        return BookingDto.from(scoped(id));
    }

    /**
     * Reserve a slot (spec §8.8).
     *
     * <p>Getting this right under concurrency needs care. Checking for conflicts and then inserting
     * is a read-then-write race, and locking the conflicting bookings does not close it: when the
     * slot is still free there are no rows to lock, so two simultaneous requests would both find it
     * empty and both insert. So the <b>resource row</b> is locked first — a row that always exists —
     * and the two transactions queue on it. The second one then runs its check after the first has
     * committed, sees the new booking, and is refused.
     *
     * <p>The unique {@code (resource, start, end)} index is the last line of defence, translated
     * into the same 400 rather than surfacing as a 500.
     */
    @PostMapping("/")
    @Transactional
    public ResponseEntity<BookingDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Resource resource = scopedResource(Body.requireLong(body, "resource"));
        Resident resident = residents.findById(Body.requireLong(body, "resident"))
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        LocalDateTime start = Times.toStorage(Body.requireDateTime(body, "start_time"));
        LocalDateTime end = Times.toStorage(Body.requireDateTime(body, "end_time"));
        requireOrderedWindow(start, end);

        resources.lockById(resource.getId())
                .orElseThrow(() -> ApiException.notFound("Not found."));

        if (!bookings.findConflicts(resource.getId(), start, end, Booking.BLOCKING, null).isEmpty()) {
            throw new FieldException("non_field_errors", ALREADY_BOOKED);
        }

        Booking booking = new Booking();
        booking.setResource(resource);
        booking.setResident(resident);
        booking.setStartTime(start);
        booking.setEndTime(end);
        apply(booking, body);

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(BookingDto.from(bookings.saveAndFlush(booking)));
        } catch (DataIntegrityViolationException raced) {
            throw new FieldException("non_field_errors", ALREADY_BOOKED);
        }
    }

    @PatchMapping("/{id}/")
    @Transactional
    public BookingDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Booking booking = scoped(id);

        LocalDateTime start = body.containsKey("start_time")
                ? Times.toStorage(Body.requireDateTime(body, "start_time")) : booking.getStartTime();
        LocalDateTime end = body.containsKey("end_time")
                ? Times.toStorage(Body.requireDateTime(body, "end_time")) : booking.getEndTime();

        if (body.containsKey("start_time") || body.containsKey("end_time")) {
            requireOrderedWindow(start, end);
            if (!bookings.findConflicts(booking.getResource().getId(), start, end,
                    Booking.BLOCKING, booking.getId()).isEmpty()) {
                throw new FieldException("non_field_errors", ALREADY_BOOKED);
            }
            booking.setStartTime(start);
            booking.setEndTime(end);
        }
        apply(booking, body);
        return BookingDto.from(bookings.save(booking));
    }

    @PutMapping("/{id}/")
    @Transactional
    public BookingDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookings.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Dry-run the same validation without persisting (spec §8.8). The calendar calls this while the
     * user drags a slot, so a clash is visible <em>before</em> they press Book rather than as a 400
     * afterwards.
     */
    @PostMapping("/quote/")
    @Transactional(readOnly = true)
    public Map<String, Object> quote(@RequestBody Map<String, Object> body) {
        Resource resource = scopedResource(Body.requireLong(body, "resource"));
        LocalDateTime start = Body.requireDateTime(body, "start_time");
        LocalDateTime end = Body.requireDateTime(body, "end_time");
        requireOrderedWindow(start, end);

        if (!bookings.findConflicts(resource.getId(), start, end, Booking.BLOCKING, null).isEmpty()) {
            throw new FieldException("non_field_errors", ALREADY_BOOKED);
        }
        BigDecimal fee = Body.asDecimal(body, "estimated_fee");
        return Map.of("available", true, "estimated_fee", fee == null ? BigDecimal.ZERO : fee);
    }

    private static void requireOrderedWindow(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new FieldException("end_time", END_BEFORE_START);
        }
    }

    private void apply(Booking booking, Map<String, Object> body) {
        if (body.containsKey("status")) {
            String status = Body.str(body, "status");
            Body.requireOneOf(status, Booking.STATUSES, "status");
            booking.setStatus(status);
        }
        if (body.containsKey("purpose")) {
            booking.setPurpose(Body.str(body, "purpose"));
        }
    }

    private Resource scopedResource(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return resources.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private Booking scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return bookings.findByIdAndResourceBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
