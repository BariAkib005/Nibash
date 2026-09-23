package com.nibash.intercom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code IntercomLog -> device -> building} (spec §6.3). */
public interface IntercomLogRepository extends JpaRepository<IntercomLog, Long> {

    Page<IntercomLog> findByDeviceBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<IntercomLog> findByDeviceBuildingIdInAndDeviceId(List<Long> buildingIds, Long deviceId, Pageable pageable);

    Optional<IntercomLog> findByIdAndDeviceBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByDeviceId(Long deviceId);

    long countByDeviceId(Long deviceId);

    /** Dashboard {@code intercom_logs}: newest first. */
    @Query("""
           select l from IntercomLog l join fetch l.device d
           where d.building.id = :buildingId
           order by l.timestamp desc, l.id desc
           """)
    List<IntercomLog> findNewest(@Param("buildingId") Long buildingId, Pageable pageable);
}
