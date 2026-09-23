package com.nibash.intercom;

import com.nibash.building.Building;
import jakarta.persistence.*;

/** A physical intercom panel. Tenant path: {@code IntercomDevice -> building}. */
@Entity
@Table(name = "intercom_devices")
public class IntercomDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
