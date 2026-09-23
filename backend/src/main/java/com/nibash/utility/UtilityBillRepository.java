package com.nibash.utility;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code UtilityBill -> meter -> unit -> building} (spec §6.3). */
public interface UtilityBillRepository extends JpaRepository<UtilityBill, Long> {

    /** Pending bills for every meter on a unit — the roll-up source for {@code generate-monthly}. */
    @Query("""
           select b from UtilityBill b
             join fetch b.meter m
           where m.unit.id = :unitId and b.status = 'pending'
           order by b.readingDate asc
           """)
    List<UtilityBill> findPendingForUnit(@Param("unitId") Long unitId);

    @Query(value = """
                   select b from UtilityBill b join fetch b.meter m join fetch m.unit u
                   where u.building.id in :buildingIds
                     and (:meterId is null or m.id = :meterId)
                     and (:unitId is null or u.id = :unitId)
                     and (:status is null or b.status = :status)
                   """,
           countQuery = """
                   select count(b) from UtilityBill b
                   where b.meter.unit.building.id in :buildingIds
                     and (:meterId is null or b.meter.id = :meterId)
                     and (:unitId is null or b.meter.unit.id = :unitId)
                     and (:status is null or b.status = :status)
                   """)
    Page<UtilityBill> search(@Param("buildingIds") List<Long> buildingIds, @Param("meterId") Long meterId,
                             @Param("unitId") Long unitId, @Param("status") String status, Pageable pageable);

    Optional<UtilityBill> findByIdAndMeterUnitBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByMeterIdAndReadingDate(Long meterId, LocalDate readingDate);

    boolean existsByMeterId(Long meterId);

    List<UtilityBill> findByIdIn(Collection<Long> ids);
}
