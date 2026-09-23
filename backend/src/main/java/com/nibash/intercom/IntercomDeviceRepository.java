package com.nibash.intercom;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code IntercomDevice -> building} (spec §6.3). */
public interface IntercomDeviceRepository extends JpaRepository<IntercomDevice, Long> {

    Page<IntercomDevice> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<IntercomDevice> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<IntercomDevice> findByBuildingIdAndIpAddress(Long buildingId, String ipAddress);
}
