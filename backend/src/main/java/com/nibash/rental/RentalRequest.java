package com.nibash.rental;

import com.nibash.common.Times;
import com.nibash.resident.Resident;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Someone asking to rent a listing. A neighbour already has a resident row ({@code tenant}); an
 * applicant from outside the building has only an account until the committee approves them, which
 * creates their resident row. Tenant path: {@code RentalRequest -> listing -> building}.
 */
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

    /** The applicant's resident row in the listing's building; null for an outsider until approved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Resident tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(nullable = false, length = 8)
    private String status = PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Listing getListing() { return listing; }
    public void setListing(Listing listing) { this.listing = listing; }
    public Resident getTenant() { return tenant; }
    public void setTenant(Resident tenant) { this.tenant = tenant; }
    public User getApplicant() { return applicant; }
    public void setApplicant(User applicant) { this.applicant = applicant; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
