package com.nibash.building;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Per-building JSON key-value store (spec §7): enabled_modules, parking_layout, … */
@Entity
@Table(name = "building_settings")
public class BuildingSetting {

    public static final String ENABLED_MODULES = "enabled_modules";
    public static final String PARKING_LAYOUT = "parking_layout";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    @Column(name = "value_json", columnDefinition = "json")
    private String valueJson;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
