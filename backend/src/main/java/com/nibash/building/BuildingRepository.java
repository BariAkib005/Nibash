package com.nibash.building;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    /** Step 3 of the home-building fallback chain (spec §4.2). */
    @Query("""
           select b from Building b
           where b.developer.id = :userId or b.primaryContact.id = :userId
           order by b.id asc
           """)
    List<Building> findOwnedBy(@Param("userId") Long userId);

    /** Same, but IDs only — used when computing tenancy (spec §6.1). */
    @Query("""
           select b.id from Building b
           where b.developer.id = :userId or b.primaryContact.id = :userId
           """)
    List<Long> findIdsOwnedBy(@Param("userId") Long userId);

    /** Back-office sees everything. */
    @Query("select b.id from Building b")
    List<Long> findAllIds();

    /** Step 4: back-office convenience — the first building in the system. */
    Optional<Building> findFirstByOrderByIdAsc();

    List<Building> findByIdInOrderByNameAsc(List<Long> ids);

    Page<Building> findByIdIn(List<Long> ids, Pageable pageable);

    Optional<Building> findByIdAndIdIn(Long id, List<Long> ids);
}
