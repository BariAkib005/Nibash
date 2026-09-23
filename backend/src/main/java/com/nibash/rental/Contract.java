package com.nibash.rental;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** The signed agreement attached to an approved request. Tenant path: {@code Contract -> request -> listing -> building}. */
@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private RentalRequest request;

    @Column(name = "contract_path", nullable = false, length = 255)
    private String contractPath;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RentalRequest getRequest() { return request; }
    public void setRequest(RentalRequest request) { this.request = request; }
    public String getContractPath() { return contractPath; }
    public void setContractPath(String contractPath) { this.contractPath = contractPath; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
}
