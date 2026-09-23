package com.nibash.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant rule (spec §6.3): a vendor is visible when it belongs to one of the caller's buildings
 * <b>or</b> is global ({@code building IS NULL}). Every read goes through that disjunction.
 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    @Query(value = """
                   select v from Vendor v left join v.building b
                   where (b.id in :buildingIds or v.building is null)
                     and (:serviceId is null or v.service.id = :serviceId)
                   """,
           countQuery = """
                   select count(v) from Vendor v left join v.building b
                   where (b.id in :buildingIds or v.building is null)
                     and (:serviceId is null or v.service.id = :serviceId)
                   """)
    Page<Vendor> findVisible(@Param("buildingIds") List<Long> buildingIds,
                             @Param("serviceId") Long serviceId, Pageable pageable);

    @Query("""
           select v from Vendor v left join v.building b
           where v.id = :id and (b.id in :buildingIds or v.building is null)
           """)
    Optional<Vendor> findVisibleById(@Param("id") Long id, @Param("buildingIds") List<Long> buildingIds);

    /** Candidates for the nearby search: visible, of the service, and actually geolocated. */
    @Query("""
           select v from Vendor v join fetch v.service left join v.building b
           where v.service.id = :serviceId
             and v.latitude is not null and v.longitude is not null
             and (b.id in :buildingIds or v.building is null)
           """)
    List<Vendor> findNearbyCandidates(@Param("serviceId") Long serviceId,
                                      @Param("buildingIds") List<Long> buildingIds);

    /** Dashboard {@code vendors}: best-rated first, own building and global. */
    @Query("""
           select v from Vendor v join fetch v.service left join v.building b
           where b.id = :buildingId or v.building is null
           order by v.rating desc nulls last, v.name asc
           """)
    List<Vendor> findBestRated(@Param("buildingId") Long buildingId, Pageable pageable);

    Optional<Vendor> findFirstByNameIgnoreCase(String name);
}
