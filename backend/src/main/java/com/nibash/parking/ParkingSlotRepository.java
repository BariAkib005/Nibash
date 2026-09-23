package com.nibash.parking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code ParkingSlot -> building} (spec §6.3). */
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    Page<ParkingSlot> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<ParkingSlot> findByBuildingIdInAndStatus(List<Long> buildingIds, String status, Pageable pageable);

    Optional<ParkingSlot> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<ParkingSlot> findByBuildingIdAndSlotNumber(Long buildingId, String slotNumber);

    List<ParkingSlot> findByBuildingIdOrderBySlotNumberAsc(Long buildingId);
}
