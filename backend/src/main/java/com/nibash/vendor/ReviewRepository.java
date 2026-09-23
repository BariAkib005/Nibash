package com.nibash.vendor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Review -> resident -> building} (spec §6.3). */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByResidentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<Review> findByResidentBuildingIdInAndVendorId(List<Long> buildingIds, Long vendorId, Pageable pageable);

    Optional<Review> findByIdAndResidentBuildingIdIn(Long id, List<Long> buildingIds);

    /** The vendor's live average — kept on the vendor row so list screens need no aggregate query. */
    @Query("select avg(r.rating) from Review r where r.vendor.id = :vendorId")
    Double averageRating(@Param("vendorId") Long vendorId);

    long countByVendorId(Long vendorId);

    /** Dashboard {@code reviews}: newest reviews of the building's own vendors. */
    @Query("""
           select r from Review r
             join fetch r.vendor v
             join fetch r.resident res
             join fetch res.user
           where v.building.id = :buildingId
           order by r.createdAt desc, r.id desc
           """)
    List<Review> findNewestForBuildingVendors(@Param("buildingId") Long buildingId, Pageable pageable);

    default BigDecimal roundedAverage(Long vendorId) {
        Double average = averageRating(vendorId);
        return average == null ? null : BigDecimal.valueOf(average).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
