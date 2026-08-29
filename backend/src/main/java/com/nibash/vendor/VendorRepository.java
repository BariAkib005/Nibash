package com.nibash.vendor;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Week 5 owns the vendor endpoints; Week 3 needs the repository because expenses and tickets can
 * point at a vendor. Tenant rule when it is exposed: own building OR global (null building).
 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {
}
