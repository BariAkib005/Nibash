package com.nibash.asset;

import com.nibash.building.Building;
import com.nibash.common.Times;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One reported lift state. {@code asset} is optional — a log with none describes "the building's
 * lift" generally. Tenant path: {@code LiftStatusLog -> building}.
 */
@Entity
@Table(name = "lift_status_logs")
public class LiftStatusLog {

    public static final List<String> STATUSES = List.of("operational", "maintenance", "out_of_order");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(nullable = false)
    private LocalDateTime timestamp = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
