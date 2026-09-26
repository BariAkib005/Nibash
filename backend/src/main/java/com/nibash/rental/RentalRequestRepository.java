package com.nibash.rental;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code RentalRequest -> listing -> building} (spec §6.3). */
public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    Page<RentalRequest> findByListingBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<RentalRequest> findByListingBuildingIdInAndListingId(List<Long> buildingIds, Long listingId, Pageable pageable);

    /**
     * What a resident sees: only requests they made or received. Managers see every request in their
     * buildings; nobody else needs a neighbour's — or an outside applicant's — name.
     */
    @Query(value = """
                   select q from RentalRequest q join q.listing l left join l.resident lr
                   where l.building.id in :buildingIds
                     and (:listingId is null or l.id = :listingId)
                     and (q.applicant.id = :userId
                          or lr.user.id = :userId
                          or (lr is null and l.listedBy.id = :userId))
                   """,
           countQuery = """
                   select count(q) from RentalRequest q join q.listing l left join l.resident lr
                   where l.building.id in :buildingIds
                     and (:listingId is null or l.id = :listingId)
                     and (q.applicant.id = :userId
                          or lr.user.id = :userId
                          or (lr is null and l.listedBy.id = :userId))
                   """)
    Page<RentalRequest> findForParty(@Param("buildingIds") List<Long> buildingIds, @Param("listingId") Long listingId,
                                     @Param("userId") Long userId, Pageable pageable);

    Optional<RentalRequest> findByIdAndListingBuildingIdIn(Long id, List<Long> buildingIds);

    /** An applicant's own requests, across buildings — an outsider has no tenancy to scope by. */
    Page<RentalRequest> findByApplicantId(Long applicantId, Pageable pageable);

    Optional<RentalRequest> findByIdAndApplicantId(Long id, Long applicantId);

    boolean existsByListingIdAndApplicantIdAndStatus(Long listingId, Long applicantId, String status);

    boolean existsByListingIdAndStatus(Long listingId, String status);

    @Query("select q.listing.id from RentalRequest q where q.listing.id in :listingIds and q.status = 'approved'")
    List<Long> findLetListingIds(@Param("listingIds") Collection<Long> listingIds);

    long countByListingId(Long listingId);
}
