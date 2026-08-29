package com.nibash.ticket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Ticket -> building} (direct FK, spec §6.3). */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
           select t from Ticket t
           where t.building.id in :buildingIds
             and (:status is null or t.status = :status)
             and (:residentId is null or t.resident.id = :residentId)
           order by t.createdAt desc, t.id desc
           """)
    Page<Ticket> search(@Param("buildingIds") List<Long> buildingIds,
                        @Param("status") String status,
                        @Param("residentId") Long residentId,
                        Pageable pageable);

    Optional<Ticket> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    long countByBuildingIdInAndStatusNotIn(List<Long> buildingIds, List<String> statuses);
}
