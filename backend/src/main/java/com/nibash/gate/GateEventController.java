package com.nibash.gate;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/gate-events/} — IsAuthenticated (spec §8.17). Guards log open/close from a phone. */
@RestController
@RequestMapping("/api/gate-events")
public class GateEventController {

    private final GateEventRepository events;
    private final BuildingRepository buildings;
    private final TenantService tenancy;
    private final ZoneId zone;

    public GateEventController(GateEventRepository events, BuildingRepository buildings,
                               TenantService tenancy, @Value("${nibash.timezone}") String timezone) {
        this.events = events;
        this.buildings = buildings;
        this.tenancy = tenancy;
        this.zone = ZoneId.of(timezone);
    }

    public record GateEventDto(Long id, Long building, String eventType, LocalDateTime timestamp,
                               Long actor, String actorName) {

        public static GateEventDto from(GateEvent e) {
            return new GateEventDto(e.getId(), e.getBuilding().getId(), e.getEventType(),
                    e.getTimestamp(), e.getActor() == null ? null : e.getActor().getId(),
                    e.getActor() == null ? null : e.getActor().getName());
        }
    }

    /** One bar of the traffic chart: how many opens/closes happened in a given hour of the day. */
    public record HourlyBucket(int hour, String eventType, long total) {
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<GateEventDto> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(events.findByBuildingIdIn(scope, pageable), GateEventDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public GateEventDto detail(@PathVariable Long id) {
        return GateEventDto.from(scoped(id));
    }

    /** One tap on the guard screen. The actor defaults to the caller — whoever is on the gate. */
    @PostMapping("/")
    @Transactional
    public ResponseEntity<GateEventDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        String eventType = Body.requireStr(body, "event_type");
        Body.requireOneOf(eventType, GateEvent.EVENT_TYPES, "event_type");

        GateEvent event = new GateEvent();
        event.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        event.setEventType(eventType);
        event.setActor(caller);

        LocalDateTime timestamp = Body.asDateTime(body, "timestamp");
        event.setTimestamp(timestamp == null ? Times.now() : timestamp);
        return ResponseEntity.status(HttpStatus.CREATED).body(GateEventDto.from(events.save(event)));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        events.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Gate traffic by hour of day (spec §8.17) — the chart on the guard's log screen.
     *
     * <p>Timestamps are stored in UTC, but "which hour is the gate busiest" is a question about the
     * building's own clock: a 6pm Dhaka rush would otherwise appear at noon and the chart would be
     * quietly wrong. The bucket labels are therefore shifted into {@code nibash.timezone} before
     * being returned. A fixed-offset zone makes this an exact relabelling — no two UTC hours fold
     * into one local hour — so the counts themselves need no re-aggregation.
     */
    @GetMapping("/analytics/")
    @Transactional(readOnly = true)
    public Map<String, Object> analytics(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return Map.of("results", List.of());
        }
        int offsetHours = ZonedDateTime.now(zone).getOffset().getTotalSeconds() / 3600;

        List<HourlyBucket> results = events.hourlyHistogram(scope).stream()
                .map(row -> new HourlyBucket(
                        Math.floorMod(((Number) row[0]).intValue() + offsetHours, 24),
                        (String) row[1],
                        ((Number) row[2]).longValue()))
                .sorted(Comparator.comparingInt(HourlyBucket::hour).thenComparing(HourlyBucket::eventType))
                .toList();
        return Map.of("results", results);
    }

    private GateEvent scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return events.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
