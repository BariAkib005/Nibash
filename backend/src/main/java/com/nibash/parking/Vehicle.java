package com.nibash.parking;

import com.nibash.common.Times;
import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** A resident's vehicle, optionally parked in a slot. Tenant path: {@code Vehicle -> resident -> building}. */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    public static final List<String> TYPES = List.of("car", "motorbike", "bicycle", "other");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_slot_id")
    private ParkingSlot parkingSlot;

    @Column(name = "vehicle_number", nullable = false, length = 50)
    private String vehicleNumber;

    @Column(nullable = false, length = 10)
    private String type = "car";

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public ParkingSlot getParkingSlot() { return parkingSlot; }
    public void setParkingSlot(ParkingSlot parkingSlot) { this.parkingSlot = parkingSlot; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
