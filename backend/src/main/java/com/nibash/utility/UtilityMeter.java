package com.nibash.utility;

import com.nibash.unit.Unit;
import jakarta.persistence.*;

/**
 * A per-unit meter. Tenant path: {@code UtilityMeter -> unit -> building}. Week 5 owns the CRUD;
 * Week 3 needs the mapping because {@code generate-monthly} can roll pending utility bills into
 * the month's invoice.
 */
@Entity
@Table(name = "utility_meters")
public class UtilityMeter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 11)
    private String type;

    @Column(name = "meter_number", nullable = false, length = 100)
    private String meterNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMeterNumber() { return meterNumber; }
    public void setMeterNumber(String meterNumber) { this.meterNumber = meterNumber; }
}
