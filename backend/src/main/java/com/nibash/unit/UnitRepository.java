package com.nibash.unit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant path: {@code Unit → building} (direct FK, spec §6.3). Every finder therefore takes the
 * caller's allowed building IDs.
 */
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Page<Unit> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<Unit> findByBuildingIdInAndStatus(List<Long> buildingIds, String status, Pageable pageable);

    /** Detail/update/delete resolve through the scoped query, so out-of-tenant IDs 404. */
    Optional<Unit> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    List<Unit> findByBuildingIdOrderByFloorAscUnitNumberAsc(Long buildingId);

    Optional<Unit> findByBuildingIdAndUnitNumber(Long buildingId, String unitNumber);

    long countByBuildingIdIn(List<Long> buildingIds);

    long countByBuildingIdInAndStatusIn(List<Long> buildingIds, List<String> statuses);
}
