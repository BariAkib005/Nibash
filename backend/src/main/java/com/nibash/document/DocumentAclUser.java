package com.nibash.document;

import com.nibash.user.User;
import jakarta.persistence.*;

/**
 * A per-user grant on a document. Stored and editable, but — as in the original system — not
 * enforced on reads (spec §8.10, §15.6); see the README.
 */
@Entity
@Table(name = "document_acl_users")
public class DocumentAclUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "can_view", nullable = false)
    private boolean canView = true;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isCanView() { return canView; }
    public void setCanView(boolean canView) { this.canView = canView; }
    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }
}
