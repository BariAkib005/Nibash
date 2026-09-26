package com.nibash.membership;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Invitation -> building} (spec §6.3). */
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /** The management list: the scoped buildings' invitations still waiting, optionally for some roles. */
    @Query(value = """
                   select i from Invitation i join fetch i.building left join fetch i.unit left join fetch i.invitedBy
                   where i.building.id in :buildingIds
                     and i.role in :roles
                     and i.acceptedAt is null
                   """,
           countQuery = """
                   select count(i) from Invitation i
                   where i.building.id in :buildingIds and i.role in :roles and i.acceptedAt is null
                   """)
    Page<Invitation> findOpen(@Param("buildingIds") List<Long> buildingIds,
                              @Param("roles") Collection<String> roles, Pageable pageable);

    Optional<Invitation> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<Invitation> findByTokenHash(String tokenHash);

    /**
     * Accepting reads the invitation under a write lock, so two submissions of the same link
     * serialise and the second one sees it already used instead of creating a second account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :tokenHash")
    Optional<Invitation> lockByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("""
           select count(i) > 0 from Invitation i
           where i.building.id = :buildingId
             and lower(i.email) = lower(:email)
             and i.acceptedAt is null
             and i.expiresAt > :now
           """)
    boolean existsOpenFor(@Param("buildingId") Long buildingId, @Param("email") String email,
                          @Param("now") LocalDateTime now);
}
