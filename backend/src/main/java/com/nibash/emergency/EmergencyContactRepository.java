package com.nibash.emergency;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code EmergencyContact -> building} (direct FK, spec §6.3). */
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    Page<EmergencyContact> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<EmergencyContact> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    List<EmergencyContact> findByBuildingIdOrderByTypeAsc(Long buildingId);
}
