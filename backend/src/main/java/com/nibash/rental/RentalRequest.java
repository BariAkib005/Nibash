package com.nibash.rental;

import com.nibash.common.Times;
import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** A resident asking to rent a listing. Tenant path: {@code RentalRequest -> listing -> building}. */
@Entity
@Table(name = "rental_requests")
public class RentalRequest {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final List<String> STATUSES = List.of(PENDING, APPROVED, REJECTED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Resident tenant;

    @Column(nullable = false, length = 8)
    private String status = PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Listing getListing() { return listing; }
    public void setListing(Listing listing) { this.listing = listing; }
    public Resident getTenant() { return tenant; }
    public void setTenant(Resident tenant) { this.tenant = tenant; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
