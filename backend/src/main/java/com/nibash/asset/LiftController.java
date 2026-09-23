package com.nibash.asset;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/lifts/status/} — IsAuthenticated CRUD of lift status logs, so whoever notices a
 * stuck lift can report it — and {@code GET /api/lifts/current/}, the latest state per lift
 * (spec §8.17).
 */
@RestController
@RequestMapping("/api/lifts")
public class LiftController {

    /** The name a log gets when it isn't tied to a specific lift asset (spec §9 {@code lifts}). */
    public static final String FALLBACK_NAME = "Building lift";

    private final LiftStatusLogRepository logs;
    private final AssetRepository assets;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public LiftController(LiftStatusLogRepository logs, AssetRepository assets, BuildingRepository buildings,
                          TenantService tenancy) {
        this.logs = logs;
        this.assets = assets;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record LiftStatusDto(Long id, Long building, Long asset, String status, LocalDateTime timestamp,
                                String name) {

        public static LiftStatusDto from(LiftStatusLog l) {
            return new LiftStatusDto(l.getId(), l.getBuilding().getId(),
                    l.getAsset() == null ? null : l.getAsset().getId(), l.getStatus(), l.getTimestamp(),
                    l.getAsset() == null ? FALLBACK_NAME : l.getAsset().getName());
        }
    }

    @GetMapping("/status/")
    @Transactional(readOnly = true)
    public PageEnvelope<LiftStatusDto> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(logs.findByBuildingIdIn(scope, pageable), LiftStatusDto::from);
    }

    /**
     * The latest log per lift: keyed on the asset, and logs with no asset keyed on their building
     * (spec §8.17). {@code {"results": [...]}}, not paginated.
     */
    @GetMapping("/current/")
    @Transactional(readOnly = true)
    public Map<String, Object> current(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        List<LiftStatusDto> results = scope.isEmpty() ? List.of() : latestPerLift(logs.findNewestFirst(scope));
        return Map.of("results", results);
    }

    /** Shared with the dashboard, which renders the same view as its {@code lifts} section. */
    public static List<LiftStatusDto> latestPerLift(List<LiftStatusLog> newestFirst) {
        Map<String, LiftStatusDto> latest = new LinkedHashMap<>();
        for (LiftStatusLog log : newestFirst) {
            String key = log.getAsset() != null ? "asset:" + log.getAsset().getId() : "building:" + log.getBuilding().getId();
            latest.putIfAbsent(key, LiftStatusDto.from(log));
        }
        return List.copyOf(latest.values());
    }

    @GetMapping("/status/{id}/")
    @Transactional(readOnly = true)
    public LiftStatusDto detail(@PathVariable Long id) {
        return LiftStatusDto.from(scoped(id));
    }

    @PostMapping("/status/")
    @Transactional
    public ResponseEntity<LiftStatusDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        LiftStatusLog log = new LiftStatusLog();
        log.setBuilding(buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found.")));
        log.setTimestamp(Times.now());
        apply(log, body, caller);
        if (log.getStatus() == null) {
            throw ApiException.badRequest("status is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(LiftStatusDto.from(logs.save(log)));
    }

    @PatchMapping("/status/{id}/")
    @Transactional
    public LiftStatusDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        LiftStatusLog log = scoped(id);
        apply(log, body, CurrentUser.require());
        return LiftStatusDto.from(logs.save(log));
    }

    @PutMapping("/status/{id}/")
    @Transactional
    public LiftStatusDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/status/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logs.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(LiftStatusLog log, Map<String, Object> body, User caller) {
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, LiftStatusLog.STATUSES, "status");
            log.setStatus(status);
        }
        if (body.containsKey("asset")) {
            Long assetId = Body.asLong(body, "asset");
            if (assetId == null) {
                log.setAsset(null);
            } else {
                Asset asset = assets.findByIdAndBuildingIdIn(assetId, tenancy.allowedBuildingIds(caller))
                        .orElseThrow(() -> ApiException.notFound("Not found."));
                if (!asset.getBuilding().getId().equals(log.getBuilding().getId())) {
                    throw ApiException.badRequest("That lift belongs to a different building.");
                }
                log.setAsset(asset);
            }
        }
        if (body.containsKey("timestamp")) {
            LocalDateTime timestamp = Body.asDateTime(body, "timestamp");
            if (timestamp != null) {
                log.setTimestamp(Times.toStorage(timestamp));
            }
        }
    }

    private LiftStatusLog scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return logs.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
