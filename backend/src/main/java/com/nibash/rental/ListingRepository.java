package com.nibash.rental;

import java.math.BigDecimal;
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
           select l from Listing l
             left join fetch l.resident r left join fetch r.user
             left join fetch l.listedBy
             left join fetch l.unit
           where l.building.id = :buildingId
           order by l.createdAt desc, l.id desc
           """)
    List<Listing> findNewest(@Param("buildingId") Long buildingId, Pageable pageable);

    /**
     * The public flats page: published listings nobody has been approved for yet, across every
     * building. The only tenancy-free listing query — it returns what the lister chose to publish.
     */
    @Query(value = """
                   select l from Listing l join fetch l.building b left join fetch l.unit
                   where l.publicListing = true
                     and not exists (select q.id from RentalRequest q where q.listing = l and q.status = 'approved')
                     and (:search is null
                          or lower(l.title) like lower(concat('%', :search, '%'))
                          or lower(l.description) like lower(concat('%', :search, '%'))
                          or lower(b.name) like lower(concat('%', :search, '%'))
                          or lower(b.address) like lower(concat('%', :search, '%')))
                     and (:maxRent is null or l.rent <= :maxRent)
                   """,
           countQuery = """
                   select count(l) from Listing l join l.building b
                   where l.publicListing = true
                     and not exists (select q.id from RentalRequest q where q.listing = l and q.status = 'approved')
                     and (:search is null
                          or lower(l.title) like lower(concat('%', :search, '%'))
                          or lower(l.description) like lower(concat('%', :search, '%'))
                          or lower(b.name) like lower(concat('%', :search, '%'))
                          or lower(b.address) like lower(concat('%', :search, '%')))
                     and (:maxRent is null or l.rent <= :maxRent)
                   """)
    Page<Listing> searchPublic(@Param("search") String search, @Param("maxRent") BigDecimal maxRent,
                               Pageable pageable);

    /** One published listing that is still open, or empty — a let or unpublished flat is a 404. */
    @Query("""
           select l from Listing l join fetch l.building left join fetch l.unit
           where l.id = :id
             and l.publicListing = true
             and not exists (select q.id from RentalRequest q where q.listing = l and q.status = 'approved')
           """)
    Optional<Listing> findOpenPublic(@Param("id") Long id);
}
