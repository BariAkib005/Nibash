package com.nibash.booking;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Resource -> building} (direct FK, spec §6.3). */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Page<Resource> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Resource> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * The resource row taken under a write lock.
     *
     * <p>Booking conflicts are checked before an insert, and a check that finds nothing locks
     * nothing — so two concurrent first-bookings of the same slot would both pass. Locking the
     * parent resource instead gives the two transactions a row they both must queue on, which is
     * what actually serialises them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> lockById(@Param("id") Long id);

    List<Resource> findByBuildingIdOrderByNameAsc(Long buildingId);
}
