package com.nibash.rental;

import com.nibash.building.Building;
import com.nibash.common.Times;
import com.nibash.resident.Resident;
import com.nibash.unit.Unit;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A flat offered for rent — by a resident, or by the building itself (a manager listing with no
 * resident row). A public listing also shows on the flats page anyone can browse. Tenant path:
 * {@code Listing -> building}.
 */
@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null when the building lists the flat itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id")
    private Resident resident;

    /** Who posted it — the resident's own account, or the manager who listed it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listed_by_id")
    private User listedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rent;

    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Column(name = "is_public", nullable = false)
    private boolean publicListing = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = Times.now();

    /** The account that decides on requests: the resident whose flat it is, else whoever listed it. */
    public User lister() {
        return resident != null ? resident.getUser() : listedBy;
    }

    public boolean isListedBy(User user) {
        User lister = lister();
        return lister != null && lister.getId().equals(user.getId());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public User getListedBy() { return listedBy; }
    public void setListedBy(User listedBy) { this.listedBy = listedBy; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getRent() { return rent; }
    public void setRent(BigDecimal rent) { this.rent = rent; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public boolean isPublicListing() { return publicListing; }
    public void setPublicListing(boolean publicListing) { this.publicListing = publicListing; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
