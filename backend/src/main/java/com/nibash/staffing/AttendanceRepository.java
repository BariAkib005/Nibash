package com.nibash.staffing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Attendance -> staff -> building} (spec §6.3). */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Page<Attendance> findByStaffBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Attendance> findByIdAndStaffBuildingIdIn(Long id, List<Long> buildingIds);

    /** The open shift, if any — what makes check-in idempotent (spec §8.5). */
    Optional<Attendance> findFirstByStaffIdAndCheckoutTimeIsNullOrderByCheckinTimeDesc(Long staffId);

    Page<Attendance> findByStaffIdOrderByCheckinTimeDesc(Long staffId, Pageable pageable);
}
