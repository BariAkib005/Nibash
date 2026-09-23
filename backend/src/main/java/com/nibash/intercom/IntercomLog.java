package com.nibash.intercom;

import com.nibash.common.Times;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** An event reported by an intercom panel. Tenant path: {@code IntercomLog -> device -> building}. */
@Entity
@Table(name = "intercom_logs")
public class IntercomLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private IntercomDevice device;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime timestamp = Times.now();

    @Column(columnDefinition = "text")
    private String details;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public IntercomDevice getDevice() { return device; }
    public void setDevice(IntercomDevice device) { this.device = device; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
