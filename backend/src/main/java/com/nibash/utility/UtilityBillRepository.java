package com.nibash.utility;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UtilityBillRepository extends JpaRepository<UtilityBill, Long> {

    /** Pending bills for every meter on a unit — the roll-up source for {@code generate-monthly}. */
    @Query("""
           select b from UtilityBill b
             join fetch b.meter m
           where m.unit.id = :unitId and b.status = 'pending'
           order by b.readingDate asc
           """)
    List<UtilityBill> findPendingForUnit(@Param("unitId") Long unitId);
}
