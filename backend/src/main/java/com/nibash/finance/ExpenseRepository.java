package com.nibash.finance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Expense -> building} (direct FK, spec §6.3). */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<Expense> findByBuildingIdInAndCategory(List<Long> buildingIds, String category, Pageable pageable);

    Optional<Expense> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /**
     * The monthly report (spec §8.3), aggregated in SQL rather than in Java so a building with
     * years of history still answers in one round trip.
     */
    @Query("""
           select function('date_format', e.date, '%Y-%m-01') as month,
                  e.category as category,
                  sum(e.amount) as total,
                  count(e) as entries
           from Expense e
           where e.building.id in :buildingIds
           group by function('date_format', e.date, '%Y-%m-01'), e.category
           order by month desc, e.category asc
           """)
    List<Object[]> monthlyReport(@Param("buildingIds") List<Long> buildingIds);
}
