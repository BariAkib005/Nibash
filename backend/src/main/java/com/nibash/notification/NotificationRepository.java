package com.nibash.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Notification -> building} (direct FK, spec §6.3). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Notification> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /** Drives the unread badge on the bell. */
    long countByBuildingIdInAndReadFalse(List<Long> buildingIds);

    List<Notification> findByBuildingIdOrderBySentAtDesc(Long buildingId, Pageable pageable);
}
