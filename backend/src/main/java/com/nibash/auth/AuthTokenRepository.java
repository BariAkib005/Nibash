package com.nibash.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    /** Fetches the user eagerly — every authenticated request needs it. */
    @EntityGraph(attributePaths = "user")
    Optional<AuthToken> findByKey(String key);

    Optional<AuthToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
