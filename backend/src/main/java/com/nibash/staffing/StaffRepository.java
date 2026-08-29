package com.nibash.staffing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Staff → building} (direct FK, spec §6.3). */
public interface StaffRepository extends JpaRepository<Staff, Long> {

    /** Step 2 of the home-building fallback chain (spec §4.2). */
    Optional<Staff> findFirstByUserIdOrderByIdAsc(Long userId);

    @Query("select s.building.id from Staff s where s.user.id = :userId")
    List<Long> findBuildingIdsForUser(@Param("userId") Long userId);

    Page<Staff> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Staff> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    List<Staff> findByBuildingIdOrderByNameAsc(Long buildingId);

    Optional<Staff> findByBuildingIdAndName(Long buildingId, String name);

    /**
     * Ticket auto-assignment, first pass (spec 8.7): the building staff whose role mentions the
     * ticket category, e.g. category "plumbing" matching role "Plumbing Technician".
     */
    @Query("""
           select s from Staff s
           where s.building.id = :buildingId
             and lower(s.role) like lower(concat('%', :category, '%'))
           order by s.id asc
           """)
    List<Staff> findByBuildingAndRoleMatching(@Param("buildingId") Long buildingId,
                                              @Param("category") String category);

    /** Users attached to the caller's buildings as staff — part of the User CRUD scope. */
    @Query("select distinct s.user.id from Staff s where s.building.id in :buildingIds and s.user is not null")
    List<Long> findUserIdsInBuildings(@Param("buildingIds") List<Long> buildingIds);
}
