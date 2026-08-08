package com.nibash.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Login and duplicate checks are case-insensitive on email (spec §4.2). */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByIdIn(List<Long> ids, Pageable pageable);

    Optional<User> findByIdAndIdIn(Long id, List<Long> ids);

    /**
     * User administration is scoped to users attached to the caller's buildings — as a resident,
     * as staff, or as the building's developer / primary contact (spec §6.3).
     */
    @Query("""
           select distinct b.developer.id from Building b where b.id in :buildingIds and b.developer is not null
           """)
    List<Long> findDeveloperIds(@Param("buildingIds") List<Long> buildingIds);

    @Query("""
           select distinct b.primaryContact.id from Building b where b.id in :buildingIds and b.primaryContact is not null
           """)
    List<Long> findPrimaryContactIds(@Param("buildingIds") List<Long> buildingIds);
}
