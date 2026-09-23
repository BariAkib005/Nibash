package com.nibash.document;

import com.nibash.common.Times;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** Who did what to a document, and when. Tenant path: {@code DocumentAuditLog -> document -> building}. */
@Entity
@Table(name = "document_audit_logs")
public class DocumentAuditLog {

    public static final String EDIT = "edit";
    public static final String DOWNLOAD = "download";
    public static final String VIEW = "view";
    public static final List<String> EVENT_TYPES = List.of(EDIT, DOWNLOAD, VIEW);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_type", nullable = false, length = 8)
    private String eventType;

    @Column(name = "event_time", nullable = false, updatable = false)
    private LocalDateTime eventTime = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
