package com.nibash.ml;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Global — no tenant path. */
public interface MlCityPriceCacheRepository extends JpaRepository<MlCityPriceCache, Long> {

    Optional<MlCityPriceCache> findByCityIgnoreCaseAndModelId(String city, Long modelId);
}
