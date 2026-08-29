package com.nibash.access;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code AccessCard -> resident -> building} (spec §6.3). */
public interface AccessCardRepository extends JpaRepository<AccessCard, Long> {

    Page<AccessCard> findByResidentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<AccessCard> findByIdAndResidentBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<AccessCard> findByCardNumber(String cardNumber);
}
