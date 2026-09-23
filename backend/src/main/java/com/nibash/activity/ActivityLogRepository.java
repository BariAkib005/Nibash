package com.nibash.activity;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Global table — reads are scoped by the acting user, see {@link ActivityLogController}. */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query(value = """
                   select a from ActivityLog a join fetch a.user u
                   where (:all = true or u.id in :userIds)
                     and (:entityType is null or a.entityType = :entityType)
                   """,
           countQuery = """
                   select count(a) from ActivityLog a
                   where (:all = true or a.user.id in :userIds)
                     and (:entityType is null or a.entityType = :entityType)
                   """)
    Page<ActivityLog> search(@Param("all") boolean all, @Param("userIds") Collection<Long> userIds,
                             @Param("entityType") String entityType, Pageable pageable);
}
