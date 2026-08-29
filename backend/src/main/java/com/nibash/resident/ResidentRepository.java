package com.nibash.resident;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Resident → building} (direct FK, spec §6.3). */
public interface ResidentRepository extends JpaRepository<Resident, Long> {

    /** Step 1 of the home-building fallback chain (spec §4.2). */
    Optional<Resident> findFirstByUserIdOrderByIdAsc(Long userId);

    Optional<Resident> findByUserIdAndBuildingId(Long userId, Long buildingId);

    @Query("select r.building.id from Resident r where r.user.id = :userId")
    List<Long> findBuildingIdsForUser(@Param("userId") Long userId);

    Page<Resident> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    /** Every resident of one building — the input set for the monthly invoice batch (spec §8.3). */
    List<Resident> findByBuildingIdOrderByIdAsc(Long buildingId);


    Optional<Resident> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * The resident directory (spec §8.1): only users who are listed, ordered by unit number, with
     * an optional search across name, unit number and email. Capped by the caller.
     */
    @Query("""
           select r from Resident r
             join fetch r.user u
             left join fetch r.unit un
           where r.building.id in :buildingIds
             and u.listed = true
             and (:search is null
                  or lower(u.name) like lower(concat('%', :search, '%'))
                  or lower(u.email) like lower(concat('%', :search, '%'))
                  or lower(coalesce(un.unitNumber, '')) like lower(concat('%', :search, '%')))
           order by coalesce(un.unitNumber, 'zzzz') asc, u.name asc
           """)
    List<Resident> searchDirectory(@Param("buildingIds") List<Long> buildingIds,
                                   @Param("search") String search,
                                   Pageable pageable);

    /** Users attached to the caller's buildings as residents — part of the User CRUD scope. */
    @Query("select distinct r.user.id from Resident r where r.building.id in :buildingIds")
    List<Long> findUserIdsInBuildings(@Param("buildingIds") List<Long> buildingIds);
}
