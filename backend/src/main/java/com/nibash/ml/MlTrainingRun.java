package com.nibash.ml;

import com.nibash.common.Times;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One training run of a model, with its parameters and metrics as JSON. Global. */
@Entity
@Table(name = "ml_training_runs")
public class MlTrainingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private MlModel model;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = Times.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "params_json", columnDefinition = "json")
    private String paramsJson;

    @Column(name = "metrics_json", columnDefinition = "json")
    private String metricsJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MlModel getModel() { return model; }
    public void setModel(MlModel model) { this.model = model; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
}
