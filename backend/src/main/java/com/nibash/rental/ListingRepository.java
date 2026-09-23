package com.nibash.rental;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Listing -> building} (spec §6.3). */
public interface ListingRepository extends JpaRepository<Listing, Long> {

    Page<Listing> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Listing> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<Listing> findFirstByBuildingIdAndTitle(Long buildingId, String title);

    /** Dashboard {@code listings}: newest first. */
    @Query("""
           select l from Listing l join fetch l.resident r join fetch r.user left join fetch l.unit
           where l.building.id = :buildingId
           order by l.createdAt desc, l.id desc
           """)
    List<Listing> findNewest(@Param("buildingId") Long buildingId, Pageable pageable);
}
