package com.nibash.document;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.Roles;
import com.nibash.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The two ACL resources — {@code /api/document-acl-users/} and {@code /api/document-acl-roles/} —
 * CommitteeOrAdmin CRUD (spec §8.10). Both filter on {@code ?document_id=}. Grants are stored but
 * not enforced on reads (§15.6).
 */
@RestController
public class DocumentAclController {

    private final DocumentAclUserRepository userGrants;
    private final DocumentAclRoleRepository roleGrants;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final TenantService tenancy;

    public DocumentAclController(DocumentAclUserRepository userGrants, DocumentAclRoleRepository roleGrants,
                                 DocumentRepository documents, UserRepository users, TenantService tenancy) {
        this.userGrants = userGrants;
        this.roleGrants = roleGrants;
        this.documents = documents;
        this.users = users;
        this.tenancy = tenancy;
    }

    public record UserGrantDto(Long id, Long document, Long user, boolean canView, boolean canEdit, String userName) {

        public static UserGrantDto from(DocumentAclUser g) {
            return new UserGrantDto(g.getId(), g.getDocument().getId(), g.getUser().getId(), g.isCanView(),
                    g.isCanEdit(), g.getUser().getName());
        }
    }

    public record RoleGrantDto(Long id, Long document, String role, boolean canView, boolean canEdit) {

        public static RoleGrantDto from(DocumentAclRole g) {
            return new RoleGrantDto(g.getId(), g.getDocument().getId(), g.getRole(), g.isCanView(), g.isCanEdit());
        }
    }

    // ---------------------------------------------------------------- per-user grants

    @GetMapping("/api/document-acl-users/")
    @Transactional(readOnly = true)
    public PageEnvelope<UserGrantDto> listUsers(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(name = "building_id", required = false) Long buildingId,
                                                @RequestParam(name = "document_id", required = false) Long documentId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("id"));
        var rows = documentId == null
                ? userGrants.findByDocumentBuildingIdIn(scope, pageable)
                : userGrants.findByDocumentBuildingIdInAndDocumentId(scope, documentId, pageable);
        return PageEnvelope.of(rows, UserGrantDto::from);
    }

    @GetMapping("/api/document-acl-users/{id}/")
    @Transactional(readOnly = true)
    public UserGrantDto userDetail(@PathVariable Long id) {
        return UserGrantDto.from(scopedUserGrant(id));
    }

    @PostMapping("/api/document-acl-users/")
    @Transactional
    public ResponseEntity<UserGrantDto> createUser(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Document document = scopedDocument(Body.requireLong(body, "document"));
        var user = users.findById(Body.requireLong(body, "user")).orElseThrow(() -> ApiException.notFound("Not found."));
        if (userGrants.findByDocumentIdAndUserId(document.getId(), user.getId()).isPresent()) {
            throw ApiException.badRequest("This user already has a grant on this document.");
        }
        DocumentAclUser grant = new DocumentAclUser();
        grant.setDocument(document);
        grant.setUser(user);
        applyUser(grant, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserGrantDto.from(userGrants.save(grant)));
    }

    @PatchMapping("/api/document-acl-users/{id}/")
    @Transactional
    public UserGrantDto updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        DocumentAclUser grant = scopedUserGrant(id);
        applyUser(grant, body);
        return UserGrantDto.from(userGrants.save(grant));
    }

    @PutMapping("/api/document-acl-users/{id}/")
    @Transactional
    public UserGrantDto replaceUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateUser(id, body);
    }

    @DeleteMapping("/api/document-acl-users/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        Policy.requireManager();
        userGrants.delete(scopedUserGrant(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- per-role grants

    @GetMapping("/api/document-acl-roles/")
    @Transactional(readOnly = true)
    public PageEnvelope<RoleGrantDto> listRoles(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(name = "building_id", required = false) Long buildingId,
                                                @RequestParam(name = "document_id", required = false) Long documentId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("role"));
        var rows = documentId == null
                ? roleGrants.findByDocumentBuildingIdIn(scope, pageable)
                : roleGrants.findByDocumentBuildingIdInAndDocumentId(scope, documentId, pageable);
        return PageEnvelope.of(rows, RoleGrantDto::from);
    }

    @GetMapping("/api/document-acl-roles/{id}/")
    @Transactional(readOnly = true)
    public RoleGrantDto roleDetail(@PathVariable Long id) {
        return RoleGrantDto.from(scopedRoleGrant(id));
    }

    @PostMapping("/api/document-acl-roles/")
    @Transactional
    public ResponseEntity<RoleGrantDto> createRole(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Document document = scopedDocument(Body.requireLong(body, "document"));
        String role = Body.requireStr(body, "role");
        if (!Roles.isValid(role)) {
            throw ApiException.badRequest("role must be one of " + Roles.ALL);
        }
        if (roleGrants.findByDocumentIdAndRole(document.getId(), role).isPresent()) {
            throw ApiException.badRequest("This role already has a grant on this document.");
        }
        DocumentAclRole grant = new DocumentAclRole();
        grant.setDocument(document);
        grant.setRole(role);
        applyRole(grant, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleGrantDto.from(roleGrants.save(grant)));
    }

    @PatchMapping("/api/document-acl-roles/{id}/")
    @Transactional
    public RoleGrantDto updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        DocumentAclRole grant = scopedRoleGrant(id);
        applyRole(grant, body);
        return RoleGrantDto.from(roleGrants.save(grant));
    }

    @PutMapping("/api/document-acl-roles/{id}/")
    @Transactional
    public RoleGrantDto replaceRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateRole(id, body);
    }

    @DeleteMapping("/api/document-acl-roles/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        Policy.requireManager();
        roleGrants.delete(scopedRoleGrant(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- helpers

    private void applyUser(DocumentAclUser grant, Map<String, Object> body) {
        if (body.containsKey("can_view")) {
            grant.setCanView(Body.asBool(body, "can_view"));
        }
        if (body.containsKey("can_edit")) {
            grant.setCanEdit(Body.asBool(body, "can_edit"));
        }
    }

    private void applyRole(DocumentAclRole grant, Map<String, Object> body) {
        if (body.containsKey("can_view")) {
            grant.setCanView(Body.asBool(body, "can_view"));
        }
        if (body.containsKey("can_edit")) {
            grant.setCanEdit(Body.asBool(body, "can_edit"));
        }
    }

    private Document scopedDocument(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return documents.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private DocumentAclUser scopedUserGrant(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return userGrants.findByIdAndDocumentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private DocumentAclRole scopedRoleGrant(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return roleGrants.findByIdAndDocumentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
