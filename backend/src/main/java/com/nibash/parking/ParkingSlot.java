package com.nibash.parking;

import com.nibash.building.Building;
import jakarta.persistence.*;
import java.util.List;

/** One bay in the building's parking grid. Tenant path: {@code ParkingSlot -> building}. */
@Entity
@Table(name = "parking_slots")
public class ParkingSlot {

    public static final String AVAILABLE = "available";
    public static final String OCCUPIED = "occupied";
    public static final String RESERVED = "reserved";
    public static final List<String> STATUSES = List.of(AVAILABLE, OCCUPIED, RESERVED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "slot_number", nullable = false, length = 50)
    private String slotNumber;

    @Column(nullable = false, length = 9)
    private String status = AVAILABLE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getSlotNumber() { return slotNumber; }
    public void setSlotNumber(String slotNumber) { this.slotNumber = slotNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
