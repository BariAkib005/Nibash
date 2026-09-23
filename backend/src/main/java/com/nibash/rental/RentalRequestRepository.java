package com.nibash.rental;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code RentalRequest -> listing -> building} (spec §6.3). */
public interface RentalRequestRepository extends JpaRepository<RentalRequest, Long> {

    Page<RentalRequest> findByListingBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<RentalRequest> findByListingBuildingIdInAndListingId(List<Long> buildingIds, Long listingId, Pageable pageable);

    Optional<RentalRequest> findByIdAndListingBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByListingIdAndTenantIdAndStatus(Long listingId, Long tenantId, String status);

    boolean existsByListingIdAndStatus(Long listingId, String status);

    long countByListingId(Long listingId);
}
