package com.nibash.finance;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bill types are global (spec §8.3) — no tenant filter applies. */
public interface BillTypeRepository extends JpaRepository<BillType, Long> {

    Page<BillType> findAllByOrderByNameAsc(Pageable pageable);

    Optional<BillType> findByName(String name);
}
