package com.nibash.poll;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Vote -> poll -> building} (spec §6.3). */
public interface VoteRepository extends JpaRepository<Vote, Long> {

    Page<Vote> findByPollBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Vote> findByIdAndPollBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByPollIdAndResidentId(Long pollId, Long residentId);

    Optional<Vote> findByPollIdAndResidentId(Long pollId, Long residentId);

    long countByPollId(Long pollId);

    /** Tally per option in one query, so results do not fan out into a query per choice. */
    @Query("select v.option.id, count(v) from Vote v where v.poll.id = :pollId group by v.option.id")
    List<Object[]> tally(@Param("pollId") Long pollId);
}
