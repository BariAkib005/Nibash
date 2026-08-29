package com.nibash.emergency;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Emergency -> building} (direct FK, spec §6.3). */
public interface EmergencyRepository extends JpaRepository<Emergency, Long> {

    Page<Emergency> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Emergency> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);
}
