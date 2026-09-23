package com.nibash.ml;

import com.nibash.common.Times;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A registered rent-estimation model. Global — no tenant path (spec §6.3). */
@Entity
@Table(name = "ml_models")
public class MlModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(name = "artifact_path", nullable = false, length = 255)
    private String artifactPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = Times.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getArtifactPath() { return artifactPath; }
    public void setArtifactPath(String artifactPath) { this.artifactPath = artifactPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
