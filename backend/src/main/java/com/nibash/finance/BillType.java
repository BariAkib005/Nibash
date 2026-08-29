package com.nibash.finance;

import jakarta.persistence.*;

/** A global (not building-scoped) charge category: Service Charge, Utility, Parking (spec §8.3). */
@Entity
@Table(name = "bill_types")
public class BillType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
