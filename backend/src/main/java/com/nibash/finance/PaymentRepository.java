package com.nibash.finance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code Payment -> invoice -> building} (spec §6.3). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByInvoiceBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<Payment> findByIdAndInvoiceBuildingIdIn(Long id, List<Long> buildingIds);

    long countByInvoiceId(Long invoiceId);
}
