package com.nibash.document;

import jakarta.persistence.*;

/** A per-role grant on a document. Stored, not enforced on reads (spec §15.6). */
@Entity
@Table(name = "document_acl_roles")
public class DocumentAclRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(name = "can_view", nullable = false)
    private boolean canView = true;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isCanView() { return canView; }
    public void setCanView(boolean canView) { this.canView = canView; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
}
