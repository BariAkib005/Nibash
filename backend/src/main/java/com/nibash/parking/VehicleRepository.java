package com.nibash.parking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Vehicle -> resident -> building} (spec §6.3). */
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Page<Vehicle> findByResidentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<Vehicle> findByResidentBuildingIdInAndResidentId(List<Long> buildingIds, Long residentId, Pageable pageable);

    Optional<Vehicle> findByIdAndResidentBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<Vehicle> findByVehicleNumberIgnoreCase(String vehicleNumber);

    Optional<Vehicle> findFirstByParkingSlotId(Long slotId);

    boolean existsByParkingSlotId(Long slotId);

    /** Dashboard {@code vehicles}: every vehicle of the building's residents. */
    @Query("""
           select v from Vehicle v join fetch v.resident r join fetch r.user left join fetch v.parkingSlot
           where r.building.id = :buildingId
           order by v.vehicleNumber asc
           """)
    List<Vehicle> findForBuilding(@Param("buildingId") Long buildingId);
}
