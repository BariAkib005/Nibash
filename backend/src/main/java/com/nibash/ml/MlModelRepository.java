package com.nibash.ml;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Global — no tenant path. */
public interface MlModelRepository extends JpaRepository<MlModel, Long> {

    /** "The newest model" the estimator uses (spec §8.22). */
    Optional<MlModel> findFirstByOrderByCreatedAtDescIdDesc();

    Optional<MlModel> findByNameAndVersion(String name, String version);
}
