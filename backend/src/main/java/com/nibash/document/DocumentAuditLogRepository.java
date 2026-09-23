package com.nibash.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code DocumentAuditLog -> document -> building} (spec §6.3). */
public interface DocumentAuditLogRepository extends JpaRepository<DocumentAuditLog, Long> {

    Page<DocumentAuditLog> findByDocumentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<DocumentAuditLog> findByDocumentBuildingIdInAndDocumentId(List<Long> buildingIds, Long documentId,
                                                                   Pageable pageable);

    Optional<DocumentAuditLog> findByIdAndDocumentBuildingIdIn(Long id, List<Long> buildingIds);

    @Query("""
           select a from DocumentAuditLog a join fetch a.user
           where a.document.id = :documentId
           order by a.eventTime desc, a.id desc
           """)
    List<DocumentAuditLog> findForDocument(@Param("documentId") Long documentId);

    long countByDocumentIdAndEventType(Long documentId, String eventType);
}
