package com.nibash.visitor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Appointment -> building} (direct FK, spec §6.3). */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Appointment> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * The scan lookup (spec §8.6). Scoping the token to the caller's buildings is what stops a
     * guard at one gate from checking in a visitor expected at another building.
     */
    Optional<Appointment> findByQrTokenAndBuildingIdIn(String qrToken, List<Long> buildingIds);

    boolean existsByQrToken(String qrToken);

    long countByBuildingIdInAndScheduledTimeBetween(List<Long> buildingIds,
                                                    LocalDateTime from, LocalDateTime to);
}
