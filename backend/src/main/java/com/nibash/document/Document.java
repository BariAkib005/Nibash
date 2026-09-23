package com.nibash.document;

import com.nibash.building.Building;
import com.nibash.common.Times;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A stored file with a version chain: a new version points at its predecessor through
 * {@code parent} and carries {@code version = parent.version + 1}; the predecessor goes inactive,
 * so "active" always means "the current version". Tenant path: {@code Document -> building}.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Document parent;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Document getParent() { return parent; }
    public void setParent(Document parent) { this.parent = parent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
