package com.nibash.chat;

import com.nibash.common.Times;
import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Membership of a resident in a room. Tenant path: {@code RoomMember -> room -> building}. */
@Entity
@Table(name = "room_members")
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ChatRoom getRoom() { return room; }
    public void setRoom(ChatRoom room) { this.room = room; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
