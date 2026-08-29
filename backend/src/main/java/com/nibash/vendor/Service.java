package com.nibash.vendor;

import jakarta.persistence.*;

/** A global service taxonomy node (Plumbing, Electrical, ...). Week 5 exposes the CRUD. */
@Entity
@Table(name = "services")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Service parent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Service getParent() { return parent; }
    public void setParent(Service parent) { this.parent = parent; }
}
