package com.nibash.booking;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Booking -> resource -> building} (spec §6.3). */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByResourceBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Booking> findByIdAndResourceBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * Overlap test (spec §8.8): two windows clash when each starts before the other ends. Cancelled
     * bookings are excluded, and {@code excludeId} lets an update ignore the row being edited.
     */
    @Query("""
           select b from Booking b
           where b.resource.id = :resourceId
             and b.status in :blocking
             and (:excludeId is null or b.id <> :excludeId)
             and b.startTime < :endTime
             and b.endTime > :startTime
           """)
    List<Booking> findConflicts(@Param("resourceId") Long resourceId,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("blocking") List<String> blocking,
                                @Param("excludeId") Long excludeId);

    /**
     * The same test taken under a write lock, run again <b>inside</b> the insert transaction.
     *
     * <p>Without this, two residents booking the same slot at the same instant both read an empty
     * conflict list and both insert. The lock makes the second one wait until the first commits, so
     * exactly one wins (plan §0.4 lists this among the rules that are never cut).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select b from Booking b
           where b.resource.id = :resourceId
             and b.status in :blocking
             and b.startTime < :endTime
             and b.endTime > :startTime
           """)
    List<Booking> lockConflicts(@Param("resourceId") Long resourceId,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("blocking") List<String> blocking);

    /** The availability window for one resource (spec §8.8). */
    @Query("""
           select b from Booking b
           where b.resource.id = :resourceId
             and b.endTime >= :startFrom
             and b.startTime <= :endTo
           order by b.startTime asc
           """)
    List<Booking> findInWindow(@Param("resourceId") Long resourceId,
                               @Param("startFrom") LocalDateTime startFrom,
                               @Param("endTo") LocalDateTime endTo);
}
