package com.nibash.asset;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code LiftStatusLog -> building} (spec §6.3). */
public interface LiftStatusLogRepository extends JpaRepository<LiftStatusLog, Long> {

    Page<LiftStatusLog> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<LiftStatusLog> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByAssetId(Long assetId);

    long countByBuildingId(Long buildingId);

    /** Every log of the building, newest first — {@code current/} keeps the first seen per lift. */
    @Query("""
           select l from LiftStatusLog l left join fetch l.asset
           where l.building.id in :buildingIds
           order by l.timestamp desc, l.id desc
           """)
    List<LiftStatusLog> findNewestFirst(@Param("buildingIds") List<Long> buildingIds);
}
