package com.nibash.community;

import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.util.List;

/** An RSVP. Unique per (event, resident), so one row per person per event (spec §8.19). */
@Entity
@Table(name = "event_attendees")
public class EventAttendee {

    public static final String INTERESTED = "interested";
    public static final List<String> STATUSES = List.of(INTERESTED, "going", "not_going");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false, length = 11)
    private String status = INTERESTED;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
