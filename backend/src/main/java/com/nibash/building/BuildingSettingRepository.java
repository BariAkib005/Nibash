package com.nibash.building;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingSettingRepository extends JpaRepository<BuildingSetting, Long> {

    Optional<BuildingSetting> findByBuildingIdAndKeyName(Long buildingId, String keyName);

    List<BuildingSetting> findByBuildingId(Long buildingId);
}
