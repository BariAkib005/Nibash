package com.nibash.gate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code GateEvent -> building} (direct FK, spec §6.3). */
public interface GateEventRepository extends JpaRepository<GateEvent, Long> {

    Page<GateEvent> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<GateEvent> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * Traffic by hour of day and event type (spec §8.17). Grouped in SQL so the chart stays cheap
     * however long the gate log grows.
     */
    @Query("""
           select hour(g.timestamp) as hour, g.eventType as eventType, count(g) as total
           from GateEvent g
           where g.building.id in :buildingIds
           group by hour(g.timestamp), g.eventType
           order by hour(g.timestamp) asc, g.eventType asc
           """)
    List<Object[]> hourlyHistogram(@Param("buildingIds") List<Long> buildingIds);
}
