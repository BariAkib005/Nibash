package com.nibash.asset;

import com.nibash.building.Building;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

/** Building equipment with a warranty — lifts, generators, pumps. Tenant path: {@code Asset -> building}. */
@Entity
@Table(name = "assets")
public class Asset {

    public static final String OPERATIONAL = "operational";
    public static final String UNDER_MAINTENANCE = "under_maintenance";
    public static final List<String> STATUSES = List.of(OPERATIONAL, UNDER_MAINTENANCE, "out_of_service", "decommissioned");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_expiry")
    private LocalDate warrantyExpiry;

    @Column(nullable = false, length = 18)
    private String status = OPERATIONAL;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public LocalDate getWarrantyExpiry() { return warrantyExpiry; }
    public void setWarrantyExpiry(LocalDate warrantyExpiry) { this.warrantyExpiry = warrantyExpiry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
