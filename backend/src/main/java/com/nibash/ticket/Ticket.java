package com.nibash.ticket;

import com.nibash.building.Building;
import com.nibash.resident.Resident;
import com.nibash.staffing.Staff;
import com.nibash.vendor.Vendor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A maintenance request. Tenant path: {@code Ticket -> building}. */
@Entity
@Table(name = "tickets")
public class Ticket {

    public static final String OPEN = "open";
    public static final String IN_PROGRESS = "in_progress";
    public static final String RESOLVED = "resolved";
    public static final String CLOSED = "closed";
    public static final List<String> STATUSES = List.of(OPEN, IN_PROGRESS, RESOLVED, CLOSED);
    public static final List<String> PRIORITIES = List.of("low", "medium", "high");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 12)
    private String status = OPEN;

    @Column(nullable = false, length = 6)
    private String priority = "medium";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private Staff assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_vendor_id")
    private Vendor serviceVendor;

    /** Images are read nested on every ticket (spec §8.7), so they hang off the aggregate root. */
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<TicketImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Staff getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Staff assignedTo) { this.assignedTo = assignedTo; }
    public Vendor getServiceVendor() { return serviceVendor; }
    public void setServiceVendor(Vendor serviceVendor) { this.serviceVendor = serviceVendor; }
    public List<TicketImage> getImages() { return images; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
