package com.nibash.asset;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code AssetMaintenance -> asset -> building} (spec §6.3). */
public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, Long> {

    Page<AssetMaintenance> findByAssetBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<AssetMaintenance> findByAssetBuildingIdInAndAssetId(List<Long> buildingIds, Long assetId, Pageable pageable);

    Optional<AssetMaintenance> findByIdAndAssetBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByAssetIdAndDescription(Long assetId, String description);

    /** Dashboard {@code asset_maintenance}: newest by scheduled date. */
    @Query("""
           select m from AssetMaintenance m join fetch m.asset a left join fetch m.vendor
           where a.building.id = :buildingId
           order by m.scheduledDate desc, m.id desc
           """)
    List<AssetMaintenance> findNewest(@Param("buildingId") Long buildingId, Pageable pageable);
}
