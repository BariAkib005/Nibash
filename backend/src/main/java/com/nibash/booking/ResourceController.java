package com.nibash.booking;

import com.nibash.auth.CurrentUser;
import com.nibash.booking.BookingController.BookingDto;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/resources/} — CommitteeOrAdmin (spec §8.8). */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public ResourceController(ResourceRepository resources, BookingRepository bookings,
                              BuildingRepository buildings, TenantService tenancy) {
        this.resources = resources;
        this.bookings = bookings;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record ResourceDto(Long id, String name, Integer capacity, String location,
                              Long building, String type) {

        public static ResourceDto from(Resource r) {
            return new ResourceDto(r.getId(), r.getName(), r.getCapacity(), r.getLocation(),
                    r.getBuilding().getId(), r.getType());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ResourceDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(resources.findByBuildingIdIn(scope, pageable), ResourceDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ResourceDto detail(@PathVariable Long id) {
        return ResourceDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ResourceDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        Resource resource = new Resource();
        resource.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        resource.setName(Body.requireStr(body, "name"));
        apply(resource, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourceDto.from(resources.save(resource)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ResourceDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Resource resource = scoped(id);
        if (body.containsKey("name")) {
            resource.setName(Body.requireStr(body, "name"));
        }
        apply(resource, body);
        return ResourceDto.from(resources.save(resource));
    }

    @PutMapping("/{id}/")
    @Transactional
    public ResourceDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        resources.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Bookings overlapping a window (spec §8.8) — what the calendar paints as busy before the user
     * picks a slot. Defaults to the coming week when no window is given.
     */
    @GetMapping("/{id}/availability/")
    @Transactional(readOnly = true)
    public Map<String, Object> availability(@PathVariable Long id,
                                            @RequestParam(name = "start_from", required = false) String startFrom,
                                            @RequestParam(name = "end_to", required = false) String endTo) {
        Resource resource = scoped(id);
        LocalDateTime from = parse(startFrom, "start_from", LocalDateTime.now());
        LocalDateTime to = parse(endTo, "end_to", from.plusDays(7));

        List<BookingDto> window = bookings.findInWindow(resource.getId(), from, to).stream()
                .map(BookingDto::from)
                .toList();
        return Map.of("resource_id", resource.getId(), "bookings", window);
    }

    private void apply(Resource resource, Map<String, Object> body) {
        if (body.containsKey("capacity")) {
            Integer capacity = Body.asInt(body, "capacity");
            resource.setCapacity(capacity == null || capacity < 1 ? 1 : capacity);
        }
        if (body.containsKey("location")) {
            resource.setLocation(Body.str(body, "location"));
        }
        if (body.containsKey("type")) {
            resource.setType(Body.str(body, "type"));
        }
    }

    private static LocalDateTime parse(String value, String field, LocalDateTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            return normalized.length() == 10
                    ? java.time.LocalDate.parse(normalized).atStartOfDay()
                    : LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(field + " must be a date or date-time");
        }
    }

    private Resource scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return resources.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
