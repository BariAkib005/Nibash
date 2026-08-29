package com.nibash.access;

import com.nibash.resident.Resident;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/** A door/gate card issued to a resident. Tenant path: {@code AccessCard -> resident -> building}. */
@Entity
@Table(name = "access_cards")
public class AccessCard {

    public static final List<String> STATUSES = List.of("active", "lost", "revoked");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "card_number", nullable = false, length = 100, unique = true)
    private String cardNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(nullable = false, length = 8)
    private String status = "active";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
