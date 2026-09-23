package com.nibash.utility;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code UtilityMeter -> unit -> building} (spec §6.3). */
public interface UtilityMeterRepository extends JpaRepository<UtilityMeter, Long> {

    @Query("select m from UtilityMeter m where m.unit.building.id = :buildingId order by m.meterNumber asc")
    List<UtilityMeter> findByBuilding(@Param("buildingId") Long buildingId);

    Page<UtilityMeter> findByUnitBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<UtilityMeter> findByUnitBuildingIdInAndUnitId(List<Long> buildingIds, Long unitId, Pageable pageable);

    Optional<UtilityMeter> findByIdAndUnitBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<UtilityMeter> findByMeterNumberIgnoreCase(String meterNumber);

    Optional<UtilityMeter> findByUnitIdAndType(Long unitId, String type);
}
