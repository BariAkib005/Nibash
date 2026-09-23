package com.nibash.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** The global service catalog — no tenant path (spec §6.3). */
public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByNameIgnoreCase(String name);

    List<Service> findAllByOrderByNameAsc();
}
