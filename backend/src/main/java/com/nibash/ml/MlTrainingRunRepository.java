package com.nibash.ml;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Global — no tenant path. */
public interface MlTrainingRunRepository extends JpaRepository<MlTrainingRun, Long> {

    Page<MlTrainingRun> findByModelId(Long modelId, Pageable pageable);
}
