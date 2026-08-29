package com.nibash.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Event -> building} (direct FK, spec §6.3). */
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Event> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);
}
