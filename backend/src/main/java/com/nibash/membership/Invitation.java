package com.nibash.membership;

import com.nibash.building.Building;
import com.nibash.common.Times;
import com.nibash.unit.Unit;
import com.nibash.user.Roles;
import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A manager's offer of a place in a building. Accepting it creates (or reuses) the person's login and
 * attaches them — as a resident row for residents and committee members, a staff row for guards and
 * staff. The link's token is stored only as a SHA-256 hash. Tenant path: {@code Invitation -> building}.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

    public static final String PENDING = "pending";
    public static final String EXPIRED = "expired";
    public static final String ACCEPTED = "accepted";

    /** Admin is the building's owner and comes from signup, never from an invitation. */
    public static final List<String> ROLES = List.of(Roles.RESIDENT, Roles.COMMITTEE, Roles.GUARD, Roles.STAFF);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "is_owner", nullable = false)
    private boolean owner = false;

    /** The staff record's role (Security, Cleaning, Maintenance…) — it drives ticket auto-assignment. */
    @Column(name = "staff_role", length = 50)
    private String staffRole;

    @Column(length = 100)
    private String designation;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private User invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = Times.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_user_id")
    private User acceptedUser;

    public String status(LocalDateTime now) {
        if (acceptedAt != null) {
            return ACCEPTED;
        }
        return expiresAt.isAfter(now) ? PENDING : EXPIRED;
    }

    /** Residents and committee members live in the building; guards and staff work in it. */
    public boolean joinsAsResident() {
        return Roles.RESIDENT.equals(role) || Roles.COMMITTEE.equals(role);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public boolean isOwner() { return owner; }
    public void setOwner(boolean owner) { this.owner = owner; }
    public String getStaffRole() { return staffRole; }
    public void setStaffRole(String staffRole) { this.staffRole = staffRole; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public User getAcceptedUser() { return acceptedUser; }
    public void setAcceptedUser(User acceptedUser) { this.acceptedUser = acceptedUser; }
}
