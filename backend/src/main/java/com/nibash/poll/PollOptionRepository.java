package com.nibash.poll;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code PollOption -> poll -> building} (spec §6.3). */
public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    Page<PollOption> findByPollBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<PollOption> findByIdAndPollBuildingIdIn(Long id, List<Long> buildingIds);

    List<PollOption> findByPollIdOrderByIdAsc(Long pollId);
}
