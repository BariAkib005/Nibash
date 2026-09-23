package com.nibash.document;

import com.nibash.activity.ActivityLogService;
import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.storage.StorageService;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/documents/} — CommitteeOrAdmin (spec §8.10).
 *
 * <ul>
 *   <li><b>Create</b> takes multipart ({@code title, building, file, mime_type, parent}) or JSON
 *       with {@code file_path}; files land under {@code media/documents/}. Every create writes an
 *       {@code edit} audit row by the caller.</li>
 *   <li><b>Versions:</b> a create with {@code parent} continues that document's chain — version
 *       {@code parent + 1} — and retires the predecessor, so the active row is always the current
 *       version. {@code GET {id}/versions/} returns the whole chain, oldest first.</li>
 *   <li><b>Download</b> ({@code GET {id}/download/}) writes a {@code download} audit row and returns
 *       {@code {"file_path"}}; {@code GET {id}/audit/} is the trail, newest first.</li>
 * </ul>
 *
 * <p>ACL rows are stored but not enforced on reads, preserving the original behaviour (§15.6).
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    /** Documents may be larger than ticket photos but stay inside the servlet's 10 MB ceiling. */
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

    private final DocumentRepository documents;
    private final DocumentAuditLogRepository audit;
    private final BuildingRepository buildings;
    private final StorageService storage;
    private final ActivityLogService activity;
    private final TenantService tenancy;

    public DocumentController(DocumentRepository documents, DocumentAuditLogRepository audit,
                              BuildingRepository buildings, StorageService storage, ActivityLogService activity,
                              TenantService tenancy) {
        this.documents = documents;
        this.audit = audit;
        this.buildings = buildings;
        this.storage = storage;
        this.activity = activity;
        this.tenancy = tenancy;
    }

    public record DocumentDto(Long id, Long building, String title, String filePath, Integer version,
                              String mimeType, Long parent, boolean isActive, Long uploadedBy,
                              LocalDateTime uploadedAt, String uploadedByName) {

        public static DocumentDto from(Document d) {
            return new DocumentDto(d.getId(), d.getBuilding().getId(), d.getTitle(), d.getFilePath(),
                    d.getVersion(), d.getMimeType(), d.getParent() == null ? null : d.getParent().getId(),
                    d.isActive(), d.getUploadedBy().getId(), d.getUploadedAt(), d.getUploadedBy().getName());
        }
    }

    public record AuditDto(Long id, Long document, Long user, String eventType, LocalDateTime eventTime,
                           String userName) {

        public static AuditDto from(DocumentAuditLog a) {
            return new AuditDto(a.getId(), a.getDocument().getId(), a.getUser().getId(), a.getEventType(),
                    a.getEventTime(), a.getUser().getName());
        }
    }

    /** {@code ?is_active=true|false} and {@code ?search=} (title) narrow the list. */
    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<DocumentDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId,
                                          @RequestParam(name = "is_active", required = false) Boolean active,
                                          @RequestParam(required = false) String search) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "uploadedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        String needle = search == null || search.isBlank() ? null : search.trim();
        return PageEnvelope.of(documents.search(scope, active, needle, pageable), DocumentDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public DocumentDto detail(@PathVariable Long id) {
        return DocumentDto.from(scoped(id));
    }

    @PostMapping(value = "/", consumes = "application/json")
    @Transactional
    public ResponseEntity<DocumentDto> create(@RequestBody Map<String, Object> body) {
        return create(body, null);
    }

    /** Multipart upload — a separate handler so Spring never tries to parse the form as JSON. */
    @PostMapping(value = "/", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<DocumentDto> upload(@RequestParam Map<String, Object> form,
                                              @RequestPart(name = "file", required = false) MultipartFile file) {
        return create(form, file);
    }

    private ResponseEntity<DocumentDto> create(Map<String, Object> body, MultipartFile file) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);
        Building building = buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found."));

        Document document = new Document();
        document.setBuilding(building);
        document.setTitle(Body.requireStr(body, "title"));
        document.setUploadedBy(caller);

        if (file != null && !file.isEmpty()) {
            storage.requireAtMost(file, MAX_DOCUMENT_BYTES, "file must be 10MB or smaller");
            storage.requireType(file, StorageService.DOCUMENT_TYPES);
            document.setFilePath(storage.store(file, "documents"));
            document.setMimeType(file.getContentType());
        } else {
            document.setFilePath(Body.requireStr(body, "file_path"));
        }
        if (body.containsKey("mime_type") && Body.str(body, "mime_type") != null && !Body.str(body, "mime_type").isBlank()) {
            document.setMimeType(Body.str(body, "mime_type").trim());
        }

        Long parentId = Body.asLong(body, "parent");
        if (parentId != null) {
            Document parent = scoped(parentId);
            if (!parent.getBuilding().getId().equals(buildingId)) {
                throw ApiException.badRequest("A new version must belong to the same building as the original.");
            }
            if (documents.existsByParentId(parent.getId())) {
                throw ApiException.badRequest("That version already has a newer one — upload against the latest version.");
            }
            document.setParent(parent);
            document.setVersion(parent.getVersion() + 1);
            parent.setActive(false);
            documents.save(parent);
        }

        Document saved = documents.save(document);
        record(saved, caller, DocumentAuditLog.EDIT);
        activity.record(caller, "document", saved.getId(), saved.getParent() == null ? "uploaded" : "new_version",
                Map.of("title", saved.getTitle(), "version", saved.getVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentDto.from(saved));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public DocumentDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Document document = scoped(id);
        if (body.containsKey("title")) {
            document.setTitle(Body.requireStr(body, "title"));
        }
        if (body.containsKey("mime_type")) {
            document.setMimeType(Body.str(body, "mime_type"));
        }
        if (body.containsKey("is_active")) {
            document.setActive(Body.asBool(body, "is_active"));
        }
        Document saved = documents.save(document);
        record(saved, CurrentUser.require(), DocumentAuditLog.EDIT);
        return DocumentDto.from(saved);
    }

    @PutMapping("/{id}/")
    @Transactional
    public DocumentDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    /**
     * Hard delete. The audit table references documents with {@code ON DELETE RESTRICT}, so the
     * document's own trail goes with it; archiving ({@code is_active=false}) keeps both. A version
     * that newer versions build on cannot be removed, and deleting the current version makes its
     * predecessor current again.
     */
    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        Document document = scoped(id);
        if (documents.existsByParentId(document.getId())) {
            throw ApiException.badRequest("Delete the newer versions of this document first.");
        }
        Document parent = document.getParent();
        activity.record(CurrentUser.require(), "document", document.getId(), "deleted",
                Map.of("title", document.getTitle(), "version", document.getVersion()));
        audit.deleteAll(audit.findForDocument(document.getId()));
        documents.delete(document);
        if (parent != null && document.isActive()) {
            parent.setActive(true);
            documents.save(parent);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Writes a {@code download} audit row and returns the path the client builds the media URL from. */
    @GetMapping("/{id}/download/")
    @Transactional
    public Map<String, Object> download(@PathVariable Long id) {
        Document document = scoped(id);
        record(document, CurrentUser.require(), DocumentAuditLog.DOWNLOAD);
        return Map.of("file_path", document.getFilePath());
    }

    /** This document's audit rows, newest first — a plain array (spec §8.10). */
    @GetMapping("/{id}/audit/")
    @Transactional(readOnly = true)
    public List<AuditDto> auditTrail(@PathVariable Long id) {
        return audit.findForDocument(scoped(id).getId()).stream().map(AuditDto::from).toList();
    }

    /** The version chain containing this document, oldest first. */
    @GetMapping("/{id}/versions/")
    @Transactional(readOnly = true)
    public List<DocumentDto> versions(@PathVariable Long id) {
        Document document = scoped(id);
        List<Document> chain = new ArrayList<>();
        for (Document cursor = document; cursor != null; cursor = cursor.getParent()) {
            chain.add(cursor);
        }
        Collections.reverse(chain);
        for (var next = documents.findFirstByParentIdOrderByVersionDesc(document.getId());
             next.isPresent();
             next = documents.findFirstByParentIdOrderByVersionDesc(next.get().getId())) {
            chain.add(next.get());
        }
        return chain.stream().map(DocumentDto::from).toList();
    }

    private void record(Document document, User user, String eventType) {
        DocumentAuditLog row = new DocumentAuditLog();
        row.setDocument(document);
        row.setUser(user);
        row.setEventType(eventType);
        audit.save(row);
    }

    private Document scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return documents.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
