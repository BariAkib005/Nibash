package com.nibash.staffing;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/attendance/} — IsAuthenticated (spec §8.5). */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRepository attendance;
    private final StaffRepository staff;
    private final TenantService tenancy;

    public AttendanceController(AttendanceRepository attendance, StaffRepository staff, TenantService tenancy) {
        this.attendance = attendance;
        this.staff = staff;
        this.tenancy = tenancy;
    }

    public record AttendanceDto(Long id, Long staff, String staffName,
                                LocalDateTime checkinTime, LocalDateTime checkoutTime) {

        public static AttendanceDto from(Attendance a) {
            return new AttendanceDto(a.getId(), a.getStaff().getId(), a.getStaff().getName(),
                    a.getCheckinTime(), a.getCheckoutTime());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AttendanceDto> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "building_id", required = false) Long buildingId,
                                            @RequestParam(name = "staff_id", required = false) Long staffId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "checkinTime"));

        if (staffId != null) {
            // Resolve the staff member through the tenant filter first, so a foreign id 404s
            // instead of quietly returning an empty page.
            requireScopedStaff(staffId);
            return PageEnvelope.of(attendance.findByStaffIdOrderByCheckinTimeDesc(staffId, pageable),
                    AttendanceDto::from);
        }
        return PageEnvelope.of(attendance.findByStaffBuildingIdIn(scope, pageable), AttendanceDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AttendanceDto detail(@PathVariable Long id) {
        return AttendanceDto.from(scoped(id));
    }

    /**
     * Start a shift (spec §8.5).
     *
     * <p><b>Idempotent:</b> a staff member with an open record gets that record back with {@code
     * 200} instead of a second row. Guards tap this button on a phone at a gate and will tap it
     * twice; the contract makes the second tap harmless rather than corrupting the timesheet.
     */
    @PostMapping("/checkin/")
    @Transactional
    public ResponseEntity<AttendanceDto> checkin(@RequestBody Map<String, Object> body) {
        Staff member = requireScopedStaff(Body.requireLong(body, "staff_id"));

        var open = attendance.findFirstByStaffIdAndCheckoutTimeIsNullOrderByCheckinTimeDesc(member.getId());
        if (open.isPresent()) {
            return ResponseEntity.ok(AttendanceDto.from(open.get()));
        }

        LocalDateTime timestamp = Body.asDateTime(body, "timestamp");
        Attendance record = new Attendance();
        record.setStaff(member);
        record.setCheckinTime(timestamp == null ? Times.now() : timestamp);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttendanceDto.from(attendance.save(record)));
    }

    /** End the open shift (spec §8.5). With nothing open there is nothing to stamp, so 400. */
    @PostMapping("/checkout/")
    @Transactional
    public AttendanceDto checkout(@RequestBody Map<String, Object> body) {
        Staff member = requireScopedStaff(Body.requireLong(body, "staff_id"));

        Attendance record = attendance
                .findFirstByStaffIdAndCheckoutTimeIsNullOrderByCheckinTimeDesc(member.getId())
                .orElseThrow(() -> ApiException.badRequest("No open attendance record"));

        LocalDateTime timestamp = Body.asDateTime(body, "timestamp");
        record.setCheckoutTime(timestamp == null ? Times.now() : timestamp);
        return AttendanceDto.from(attendance.save(record));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attendance.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Staff requireScopedStaff(Long staffId) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return staff.findByIdAndBuildingIdIn(staffId, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private Attendance scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return attendance.findByIdAndStaffBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
