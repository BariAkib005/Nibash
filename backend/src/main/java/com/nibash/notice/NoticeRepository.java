package com.nibash.notice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Notice -> building} (direct FK, spec §6.3). */
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * The board query (spec §8.4). {@code now} being null switches off the live-window filter,
     * which is how {@code ?include_archived=true} is expressed without a second query.
     *
     * <p>Ordering is pinned-first then newest — the two things that decide what a resident sees
     * at the top of the board.
     */
    @Query("""
           select n from Notice n
           where n.building.id in :buildingIds
             and (:now is null
                  or (n.publishDate <= :now and (n.expiryDate is null or n.expiryDate >= :now)))
             and (:search is null
                  or lower(n.title) like lower(concat('%', :search, '%'))
                  or lower(n.body) like lower(concat('%', :search, '%')))
           order by n.pinned desc, n.publishDate desc, n.id desc
           """)
    Page<Notice> board(@Param("buildingIds") List<Long> buildingIds,
                       @Param("now") LocalDateTime now,
                       @Param("search") String search,
                       Pageable pageable);

    Optional<Notice> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);
}
