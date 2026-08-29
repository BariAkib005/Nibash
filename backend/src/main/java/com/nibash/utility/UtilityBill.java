package com.nibash.utility;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A meter reading turned into money. Tenant path: {@code UtilityBill -> meter -> unit -> building}. */
@Entity
@Table(name = "utility_bills")
public class UtilityBill {

    public static final String PENDING = "pending";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", nullable = false)
    private UtilityMeter meter;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "reading_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal readingValue = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false, length = 7)
    private String status = PENDING;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UtilityMeter getMeter() { return meter; }
    public void setMeter(UtilityMeter meter) { this.meter = meter; }
    public LocalDate getReadingDate() { return readingDate; }
    public void setReadingDate(LocalDate readingDate) { this.readingDate = readingDate; }
    public BigDecimal getReadingValue() { return readingValue; }
    public void setReadingValue(BigDecimal readingValue) { this.readingValue = readingValue; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
