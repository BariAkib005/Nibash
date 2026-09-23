package com.nibash.document;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.PageEnvelope;
import com.nibash.document.DocumentController.AuditDto;
import com.nibash.tenancy.TenantService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/document-audit/} — read-only, IsAuthenticated (spec §8.10). */
@RestController
@RequestMapping("/api/document-audit")
public class DocumentAuditController {

    private final DocumentAuditLogRepository audit;
    private final TenantService tenancy;

    public DocumentAuditController(DocumentAuditLogRepository audit, TenantService tenancy) {
        this.audit = audit;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AuditDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId,
                                       @RequestParam(name = "document_id", required = false) Long documentId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "eventTime").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = documentId == null
                ? audit.findByDocumentBuildingIdIn(scope, pageable)
                : audit.findByDocumentBuildingIdInAndDocumentId(scope, documentId, pageable);
        return PageEnvelope.of(rows, AuditDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AuditDto detail(@PathVariable Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return AuditDto.from(audit.findByIdAndDocumentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
    }
}
