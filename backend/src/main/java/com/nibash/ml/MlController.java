package com.nibash.ml;

import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The rent-price estimator (spec §8.22): CommitteeOrAdmin CRUD over the global model registry,
 * training runs and city cache, plus the public {@code POST /api/ml/price-estimate}.
 *
 * <p>The estimate is a contract-stable placeholder (§15.5): it serves the newest model's cached
 * figure for a city — {@code 200} on a hit, {@code 202} with {@code estimate: null} on a miss, where
 * a real model run would be queued.
 */
@RestController
@RequestMapping("/api/ml")
public class MlController {

    private final MlModelRepository models;
    private final MlTrainingRunRepository runs;
    private final MlCityPriceCacheRepository cache;
    private final ObjectMapper json;

    public MlController(MlModelRepository models, MlTrainingRunRepository runs, MlCityPriceCacheRepository cache,
                        ObjectMapper json) {
        this.models = models;
        this.runs = runs;
        this.cache = cache;
        this.json = json;
    }

    public record ModelDto(Long id, String name, String version, String artifactPath, LocalDateTime createdAt) {

        public static ModelDto from(MlModel m) {
            return new ModelDto(m.getId(), m.getName(), m.getVersion(), m.getArtifactPath(), m.getCreatedAt());
        }
    }

    public record RunDto(Long id, Long model, LocalDateTime startedAt, LocalDateTime completedAt,
                         String paramsJson, String metricsJson) {

        public static RunDto from(MlTrainingRun r) {
            return new RunDto(r.getId(), r.getModel().getId(), r.getStartedAt(), r.getCompletedAt(),
                    r.getParamsJson(), r.getMetricsJson());
        }
    }

    public record CacheDto(Long id, String city, String currency, BigDecimal estimate, Long model,
                           LocalDateTime computedAt) {

        public static CacheDto from(MlCityPriceCache c) {
            return new CacheDto(c.getId(), c.getCity(), c.getCurrency(), c.getEstimate(), c.getModel().getId(),
                    c.getComputedAt());
        }
    }

    // ---------------------------------------------------------------- estimate (AllowAny)

    @PostMapping("/price-estimate")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> estimate(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        String city = Body.requireStr(input, "city");
        String currency = Body.str(input, "currency");
        currency = currency == null || currency.isBlank() ? "BDT" : currency.trim().toUpperCase(java.util.Locale.ROOT);

        MlModel model = models.findFirstByOrderByCreatedAtDescIdDesc()
                .orElseThrow(() -> ApiException.notFound("No model available"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("city", city);
        var hit = cache.findByCityIgnoreCaseAndModelId(city, model.getId());
        if (hit.isPresent()) {
            out.put("estimate", hit.get().getEstimate());
            out.put("currency", hit.get().getCurrency());
            out.put("model_version", model.getVersion());
            return ResponseEntity.ok(out);
        }
        out.put("estimate", null);
        out.put("currency", currency);
        out.put("model_version", model.getVersion());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(out);
    }

    // ---------------------------------------------------------------- models

    @GetMapping("/models/")
    @Transactional(readOnly = true)
    public PageEnvelope<ModelDto> listModels(@RequestParam(defaultValue = "1") int page) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(models.findAll(pageable), ModelDto::from);
    }

    @GetMapping("/models/{id}/")
    @Transactional(readOnly = true)
    public ModelDto model(@PathVariable Long id) {
        return ModelDto.from(findModel(id));
    }

    @PostMapping("/models/")
    @Transactional
    public ResponseEntity<ModelDto> createModel(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        String name = Body.requireStr(body, "name");
        String version = Body.requireStr(body, "version");
        if (models.findByNameAndVersion(name, version).isPresent()) {
            throw ApiException.badRequest("This model version is already registered.");
        }
        MlModel model = new MlModel();
        model.setName(name);
        model.setVersion(version);
        model.setArtifactPath(Body.requireStr(body, "artifact_path"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ModelDto.from(models.save(model)));
    }

    @PatchMapping("/models/{id}/")
    @Transactional
    public ModelDto updateModel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        MlModel model = findModel(id);
        if (body.containsKey("artifact_path")) {
            model.setArtifactPath(Body.requireStr(body, "artifact_path"));
        }
        return ModelDto.from(models.save(model));
    }

    @DeleteMapping("/models/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteModel(@PathVariable Long id) {
        Policy.requireManager();
        models.delete(findModel(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- training runs

    @GetMapping("/training-runs/")
    @Transactional(readOnly = true)
    public PageEnvelope<RunDto> listRuns(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "model_id", required = false) Long modelId) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var rows = modelId == null ? runs.findAll(pageable) : runs.findByModelId(modelId, pageable);
        return PageEnvelope.of(rows, RunDto::from);
    }

    @GetMapping("/training-runs/{id}/")
    @Transactional(readOnly = true)
    public RunDto run(@PathVariable Long id) {
        return RunDto.from(runs.findById(id).orElseThrow(() -> ApiException.notFound("Not found.")));
    }

    @PostMapping("/training-runs/")
    @Transactional
    public ResponseEntity<RunDto> createRun(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        MlTrainingRun run = new MlTrainingRun();
        run.setModel(findModel(Body.requireLong(body, "model")));
        applyRun(run, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(RunDto.from(runs.save(run)));
    }

    @PatchMapping("/training-runs/{id}/")
    @Transactional
    public RunDto updateRun(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        MlTrainingRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
        applyRun(run, body);
        return RunDto.from(runs.save(run));
    }

    @DeleteMapping("/training-runs/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteRun(@PathVariable Long id) {
        Policy.requireManager();
        runs.delete(runs.findById(id).orElseThrow(() -> ApiException.notFound("Not found.")));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- city cache

    @GetMapping("/city-cache/")
    @Transactional(readOnly = true)
    public PageEnvelope<CacheDto> listCache(@RequestParam(defaultValue = "1") int page) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("city"));
        return PageEnvelope.of(cache.findAll(pageable), CacheDto::from);
    }

    @GetMapping("/city-cache/{id}/")
    @Transactional(readOnly = true)
    public CacheDto cacheRow(@PathVariable Long id) {
        return CacheDto.from(cache.findById(id).orElseThrow(() -> ApiException.notFound("Not found.")));
    }

    @PostMapping("/city-cache/")
    @Transactional
    public ResponseEntity<CacheDto> createCache(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        MlModel model = findModel(Body.requireLong(body, "model"));
        String city = Body.requireStr(body, "city");
        if (cache.findByCityIgnoreCaseAndModelId(city, model.getId()).isPresent()) {
            throw ApiException.badRequest("This model already has an estimate for " + city + ".");
        }
        MlCityPriceCache row = new MlCityPriceCache();
        row.setModel(model);
        row.setCity(city);
        applyCache(row, body);
        if (row.getEstimate() == null) {
            throw ApiException.badRequest("estimate is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(CacheDto.from(cache.save(row)));
    }

    @PatchMapping("/city-cache/{id}/")
    @Transactional
    public CacheDto updateCache(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        MlCityPriceCache row = cache.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
        applyCache(row, body);
        row.setComputedAt(Times.now());
        return CacheDto.from(cache.save(row));
    }

    @DeleteMapping("/city-cache/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteCache(@PathVariable Long id) {
        Policy.requireManager();
        cache.delete(cache.findById(id).orElseThrow(() -> ApiException.notFound("Not found.")));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- helpers

    private void applyRun(MlTrainingRun run, Map<String, Object> body) {
        if (body.containsKey("completed_at")) {
            LocalDateTime completed = Body.asDateTime(body, "completed_at");
            run.setCompletedAt(completed == null ? null : Times.toStorage(completed));
        }
        if (body.containsKey("params_json")) {
            run.setParamsJson(jsonColumn(body.get("params_json"), "params_json"));
        }
        if (body.containsKey("metrics_json")) {
            run.setMetricsJson(jsonColumn(body.get("metrics_json"), "metrics_json"));
        }
    }

    private void applyCache(MlCityPriceCache row, Map<String, Object> body) {
        if (body.containsKey("estimate")) {
            BigDecimal estimate = Body.asDecimal(body, "estimate");
            if (estimate == null || estimate.signum() < 0) {
                throw ApiException.badRequest("estimate must be zero or more");
            }
            row.setEstimate(estimate);
        }
        if (body.containsKey("currency")) {
            row.setCurrency(Body.requireStr(body, "currency").toUpperCase(java.util.Locale.ROOT));
        }
    }

    /** A JSON column accepts an object/array from the body, or a string that must parse as JSON. */
    private String jsonColumn(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof String text ? json.readTree(text).toString() : json.writeValueAsString(value);
        } catch (JacksonException malformed) {
            throw ApiException.badRequest(field + " must be valid JSON");
        }
    }

    private MlModel findModel(Long id) {
        return models.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
