package com.nibash.unit;

import com.nibash.building.Building;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "units")
public class Unit {

    public static final String AVAILABLE = "available";
    public static final String OCCUPIED = "occupied";
    public static final String SOLD = "sold";
    public static final String RENTED = "rented";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    private Integer floor;

    @Column(nullable = false, length = 10)
    private String type = "1BHK";

    @Column(name = "size_sqft", precision = 10, scale = 2)
    private BigDecimal sizeSqft;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    private String status = AVAILABLE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }
    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getSizeSqft() { return sizeSqft; }
    public void setSizeSqft(BigDecimal sizeSqft) { this.sizeSqft = sizeSqft; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
