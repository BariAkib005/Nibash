package com.nibash.chat;

import com.nibash.common.Times;
import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A chat message. Tenant path: {@code Message -> room -> building}. */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ChatRoom getRoom() { return room; }
    public void setRoom(ChatRoom room) { this.room = room; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
