package com.nibash.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code DocumentAclUser -> document -> building} (spec §6.3). */
public interface DocumentAclUserRepository extends JpaRepository<DocumentAclUser, Long> {

    Page<DocumentAclUser> findByDocumentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<DocumentAclUser> findByDocumentBuildingIdInAndDocumentId(List<Long> buildingIds, Long documentId,
                                                                  Pageable pageable);

    Optional<DocumentAclUser> findByIdAndDocumentBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<DocumentAclUser> findByDocumentIdAndUserId(Long documentId, Long userId);
}
