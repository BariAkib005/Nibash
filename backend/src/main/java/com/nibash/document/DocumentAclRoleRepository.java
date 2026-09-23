package com.nibash.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant path: {@code DocumentAclRole -> document -> building} (spec §6.3). */
public interface DocumentAclRoleRepository extends JpaRepository<DocumentAclRole, Long> {

    Page<DocumentAclRole> findByDocumentBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<DocumentAclRole> findByDocumentBuildingIdInAndDocumentId(List<Long> buildingIds, Long documentId,
                                                                  Pageable pageable);

    Optional<DocumentAclRole> findByIdAndDocumentBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<DocumentAclRole> findByDocumentIdAndRole(Long documentId, String role);
}
