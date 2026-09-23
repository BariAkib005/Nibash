package com.nibash.waste;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/waste-schedules/} — CommitteeOrAdmin (spec §8.18), plus
 * {@code GET next/?building_id=} → {@code {"next_collection": <schedule|null>}}.
 *
 * <p>The spec's "earliest {@code schedule_time >= now}" would make a weekly schedule disappear the
 * moment its first date passed, so recurring schedules are rolled forward to their next real
 * occurrence ({@code next_occurrence} on every row) and {@code next/} picks the earliest of those.
 */
@RestController
@RequestMapping("/api/waste-schedules")
public class WasteScheduleController {

    private final WasteScheduleRepository schedules;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public WasteScheduleController(WasteScheduleRepository schedules, BuildingRepository buildings,
                                   TenantService tenancy) {
        this.schedules = schedules;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record WasteDto(Long id, Long building, LocalDateTime scheduleTime, String recurring,
                           LocalDateTime nextOccurrence) {

        public static WasteDto from(WasteSchedule w, LocalDateTime now) {
            return new WasteDto(w.getId(), w.getBuilding().getId(), w.getScheduleTime(), w.getRecurring(),
                    w.nextOccurrence(now));
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<WasteDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        LocalDateTime now = Times.now();
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("scheduleTime"));
        return PageEnvelope.of(schedules.findByBuildingIdIn(scope, pageable), w -> WasteDto.from(w, now));
    }

    @GetMapping("/next/")
    @Transactional(readOnly = true)
    public Map<String, Object> next(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        Map<String, Object> out = new HashMap<>();
        out.put("next_collection", scope.isEmpty() ? null : upcoming(schedules.findByBuildingIdIn(scope), 1)
                .stream().findFirst().orElse(null));
        return out;
    }

    /** Shared with the dashboard's {@code waste} section: the next {@code limit} collections. */
    public static List<WasteDto> upcoming(List<WasteSchedule> all, int limit) {
        LocalDateTime now = Times.now();
        return all.stream()
                .map(w -> WasteDto.from(w, now))
                .filter(dto -> dto.nextOccurrence() != null)
                .sorted(Comparator.comparing(WasteDto::nextOccurrence, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public WasteDto detail(@PathVariable Long id) {
        return WasteDto.from(scoped(id), Times.now());
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<WasteDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        WasteSchedule schedule = new WasteSchedule();
        schedule.setBuilding(buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found.")));
        schedule.setScheduleTime(Times.toStorage(Body.requireDateTime(body, "schedule_time")));
        apply(schedule, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(WasteDto.from(schedules.save(schedule), Times.now()));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public WasteDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        WasteSchedule schedule = scoped(id);
        if (body.containsKey("schedule_time")) {
            schedule.setScheduleTime(Times.toStorage(Body.requireDateTime(body, "schedule_time")));
        }
        apply(schedule, body);
        return WasteDto.from(schedules.save(schedule), Times.now());
    }

    @PutMapping("/{id}/")
    @Transactional
    public WasteDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        schedules.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(WasteSchedule schedule, Map<String, Object> body) {
        if (body.containsKey("recurring")) {
            String recurring = Body.str(body, "recurring");
            recurring = recurring == null || recurring.isBlank() ? null : recurring.trim().toLowerCase(java.util.Locale.ROOT);
            if (recurring != null && !WasteSchedule.RECURRENCES.contains(recurring)) {
                throw ApiException.badRequest("recurring must be one of " + WasteSchedule.RECURRENCES + " or empty");
            }
            schedule.setRecurring(recurring);
        }
    }

    private WasteSchedule scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return schedules.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
