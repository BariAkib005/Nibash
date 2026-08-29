package com.nibash.utility;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtilityMeterRepository extends JpaRepository<UtilityMeter, Long> {

    @Query("select m from UtilityMeter m where m.unit.building.id = :buildingId order by m.meterNumber asc")
    List<UtilityMeter> findByBuilding(@Param("buildingId") Long buildingId);
}
