package com.nibash.user;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The unified user (spec §4.1): identity, credentials, business role and back-office flags all live
 * on one row. The original Django system split this across auth_user + users; the rebuild collapses it.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    /** BCrypt hash. Never serialized — see {@link com.nibash.user.UserDto}. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String role = Roles.RESIDENT;

    @Column(name = "is_listed", nullable = false)
    private boolean listed = true;

    /** Never serialized. */
    private LocalDate dob;

    /** Never serialized. */
    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    @Column(length = 255)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "is_staff", nullable = false)
    private boolean staff = false;

    @Column(name = "is_superuser", nullable = false)
    private boolean superuser = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Back-office callers bypass tenant scoping entirely (spec §3 step 3). */
    public boolean isBackOffice() {
        return staff || superuser;
    }

    /** Admin/committee are the roles allowed to write governance data (spec §5). */
    public boolean isAdminOrCommittee() {
        return Roles.ADMIN.equals(role) || Roles.COMMITTEE.equals(role);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isListed() { return listed; }
    public void setListed(boolean listed) { this.listed = listed; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getAvatarPath() { return avatarPath; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String v) { this.emergencyContactPhone = v; }
    public boolean isStaff() { return staff; }
    public void setStaff(boolean staff) { this.staff = staff; }
    public boolean isSuperuser() { return superuser; }
    public void setSuperuser(boolean superuser) { this.superuser = superuser; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
