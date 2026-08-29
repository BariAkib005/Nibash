package com.nibash.visitor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Visitor -> appointment -> building} (spec §6.3). */
public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    Page<Visitor> findByAppointmentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Visitor> findByIdAndAppointmentBuildingIdIn(Long id, List<Long> buildingIds);

    /** One visitor row per appointment — the get-or-create key for a scan. */
    Optional<Visitor> findFirstByAppointmentIdOrderByIdAsc(Long appointmentId);
}
