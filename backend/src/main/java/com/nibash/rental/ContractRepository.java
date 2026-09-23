package com.nibash.rental;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Contract -> request -> listing -> building} (spec §6.3). */
public interface ContractRepository extends JpaRepository<Contract, Long> {

    Page<Contract> findByRequestListingBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Contract> findByIdAndRequestListingBuildingIdIn(Long id, List<Long> buildingIds);

    boolean existsByRequestId(Long requestId);
}
