package com.nibash.building;

import com.nibash.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "developer_id")
    private User developer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_contact_id")
    private User primaryContact;

    @Column(name = "year_built")
    private Short yearBuilt;

    @Column(name = "num_floors")
    private Integer numFloors;

    @Column(name = "total_units")
    private Integer totalUnits;

    @Column(length = 255)
    private String website;

    /** Raw JSON array, e.g. ["Gym","Rooftop Lounge"]. */
    @Column(name = "amenities_json", columnDefinition = "json")
    private String amenitiesJson;

    @Column(name = "photo_path", length = 255)
    private String photoPath;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public User getDeveloper() { return developer; }
    public void setDeveloper(User developer) { this.developer = developer; }
    public User getPrimaryContact() { return primaryContact; }
    public void setPrimaryContact(User primaryContact) { this.primaryContact = primaryContact; }
    public Short getYearBuilt() { return yearBuilt; }
    public void setYearBuilt(Short yearBuilt) { this.yearBuilt = yearBuilt; }
    public Integer getNumFloors() { return numFloors; }
    public void setNumFloors(Integer numFloors) { this.numFloors = numFloors; }
    public Integer getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Integer totalUnits) { this.totalUnits = totalUnits; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getAmenitiesJson() { return amenitiesJson; }
    public void setAmenitiesJson(String amenitiesJson) { this.amenitiesJson = amenitiesJson; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
