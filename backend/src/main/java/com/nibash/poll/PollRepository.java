package com.nibash.poll;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Poll -> building} (direct FK, spec §6.3). */
public interface PollRepository extends JpaRepository<Poll, Long> {

    Page<Poll> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Poll> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);
}
