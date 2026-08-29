package com.nibash.gate;

import com.nibash.building.Building;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** A gate open/close, logged by a guard. Tenant path: {@code GateEvent -> building} (spec §8.17). */
@Entity
@Table(name = "gate_events")
public class GateEvent {

    public static final List<String> EVENT_TYPES = List.of("open", "close");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "event_type", nullable = false, length = 5)
    private String eventType;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public User getActor() { return actor; }
    public void setActor(User actor) { this.actor = actor; }
}
