package com.nibash.asset;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Asset -> building} (spec §6.3). */
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Page<Asset> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Asset> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    List<Asset> findByBuildingIdOrderByNameAsc(Long buildingId);

    Optional<Asset> findFirstByBuildingIdAndName(Long buildingId, String name);
}
