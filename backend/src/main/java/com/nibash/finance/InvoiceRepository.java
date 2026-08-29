package com.nibash.finance;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Invoice -> building} (direct FK, spec §6.3). */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * The list endpoint's five optional filters in one query (spec §8.3). Passing null for a filter
     * disables it, which keeps the controller free of query-building branches.
     */
    @Query("""
           select i from Invoice i
           where i.building.id in :buildingIds
             and (:residentId is null or i.resident.id = :residentId)
             and (:status is null or i.status = :status)
             and (:dueBefore is null or i.dueDate <= :dueBefore)
             and (:dueAfter is null or i.dueDate >= :dueAfter)
           order by i.createdAt desc, i.id desc
           """)
    Page<Invoice> search(@Param("buildingIds") List<Long> buildingIds,
                         @Param("residentId") Long residentId,
                         @Param("status") String status,
                         @Param("dueBefore") LocalDate dueBefore,
                         @Param("dueAfter") LocalDate dueAfter,
                         Pageable pageable);

    Optional<Invoice> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Checkout reads the row under a write lock so two concurrent payments serialise and the second
     * one sees {@code status = 'paid'} (spec §8.3). The tenant filter stays in the query, so an
     * out-of-tenant id is a 404 rather than a lock on someone else's row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.building.id in :buildingIds")
    Optional<Invoice> lockByIdAndBuildingIdIn(@Param("id") Long id,
                                              @Param("buildingIds") List<Long> buildingIds);

    /** The reminder job's selection: still pending and due tomorrow, today, or already overdue. */
    @Query("""
           select i from Invoice i
             join fetch i.resident r
             join fetch r.user u
           where i.status = 'pending' and i.dueDate <= :cutoff
           order by i.dueDate asc
           """)
    List<Invoice> findDueForReminder(@Param("cutoff") LocalDate cutoff);

    List<Invoice> findByBuildingIdOrderByCreatedAtDesc(Long buildingId);
}
