package com.nibash.waste;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code WasteSchedule -> building} (spec §6.3). */
public interface WasteScheduleRepository extends JpaRepository<WasteSchedule, Long> {

    Page<WasteSchedule> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<WasteSchedule> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    List<WasteSchedule> findByBuildingIdIn(List<Long> buildingIds);

    long countByBuildingId(Long buildingId);
}
