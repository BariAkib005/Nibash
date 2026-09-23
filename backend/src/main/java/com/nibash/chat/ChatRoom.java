package com.nibash.chat;

import com.nibash.building.Building;
import jakarta.persistence.*;

/**
 * A group chat room. Tenant path: {@code ChatRoom -> building}.
 *
 * <p>{@code is_public = false} rooms (e.g. the committee's) are visible only to their members and
 * to managers — see {@link ChatAccess}.
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_public", nullable = false)
    private boolean publicRoom = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isPublicRoom() { return publicRoom; }
    public void setPublicRoom(boolean publicRoom) { this.publicRoom = publicRoom; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
}
