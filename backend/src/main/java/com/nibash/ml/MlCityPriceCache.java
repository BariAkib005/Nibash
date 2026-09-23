package com.nibash.ml;

import com.nibash.common.Times;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A precomputed rent estimate for a city under one model — what {@code price-estimate} serves. Global. */
@Entity
@Table(name = "ml_city_price_cache")
public class MlCityPriceCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 10)
    private String currency = "BDT";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private MlModel model;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getEstimate() { return estimate; }
    public void setEstimate(BigDecimal estimate) { this.estimate = estimate; }
    public MlModel getModel() { return model; }
    public void setModel(MlModel model) { this.model = model; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }
}
