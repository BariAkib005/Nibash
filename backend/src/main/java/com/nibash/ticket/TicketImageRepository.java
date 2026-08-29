package com.nibash.ticket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code TicketImage -> ticket -> building} (spec §6.3). */
public interface TicketImageRepository extends JpaRepository<TicketImage, Long> {

    Page<TicketImage> findByTicketBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<TicketImage> findByIdAndTicketBuildingIdIn(Long id, List<Long> buildingIds);
}
