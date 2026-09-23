package com.nibash.parking;

import com.nibash.activity.ActivityLogService;
import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.building.BuildingSetting;
import com.nibash.building.BuildingSettingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * {@code /api/parking/slots/} (CommitteeOrAdmin CRUD) and the layout generator
 * {@code POST /api/parking/layout/} (spec §8.21).
 *
 * <p>Slot status is kept honest by {@link VehicleController}: assigning a vehicle occupies its slot
 * and releasing it frees the slot, so the grid never shows a parked car in an "available" bay.
 */
@RestController
@RequestMapping("/api/parking")
public class ParkingController {

    /** The spec's default when a building has never generated a layout (§9 {@code parking_layout}). */
    public static final Map<String, Object> DEFAULT_LAYOUT = Map.of("rows", 4, "columns", 6, "prefix", "P");

    private static final int MIN_SIDE = 1;
    private static final int MAX_SIDE = 12;

    private final ParkingSlotRepository slots;
    private final VehicleRepository vehicles;
    private final BuildingRepository buildings;
    private final BuildingSettingRepository settings;
    private final ActivityLogService activity;
    private final TenantService tenancy;
    private final ObjectMapper json;

    public ParkingController(ParkingSlotRepository slots, VehicleRepository vehicles, BuildingRepository buildings,
                             BuildingSettingRepository settings, ActivityLogService activity,
                             TenantService tenancy, ObjectMapper json) {
        this.slots = slots;
        this.vehicles = vehicles;
        this.buildings = buildings;
        this.settings = settings;
        this.activity = activity;
        this.tenancy = tenancy;
        this.json = json;
    }

    public record SlotDto(Long id, Long building, String slotNumber, String status) {

        public static SlotDto from(ParkingSlot s) {
            return new SlotDto(s.getId(), s.getBuilding().getId(), s.getSlotNumber(), s.getStatus());
        }
    }

    // ---------------------------------------------------------------- slots

    @GetMapping("/slots/")
    @Transactional(readOnly = true)
    public PageEnvelope<SlotDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(name = "page_size", required = false) Integer pageSize) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        // The grid needs every slot of a 12x12 layout at once; everything else keeps the page of 20.
        int size = pageSize == null ? PageEnvelope.PAGE_SIZE : Math.clamp(pageSize, 1, MAX_SIDE * MAX_SIDE);
        var pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by("slotNumber"));
        var rows = status == null || status.isBlank()
                ? slots.findByBuildingIdIn(scope, pageable)
                : slots.findByBuildingIdInAndStatus(scope, status, pageable);
        return PageEnvelope.of(rows, SlotDto::from);
    }

    @GetMapping("/slots/{id}/")
    @Transactional(readOnly = true)
    public SlotDto detail(@PathVariable Long id) {
        return SlotDto.from(scoped(id));
    }

    @PostMapping("/slots/")
    @Transactional
    public ResponseEntity<SlotDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        String number = Body.requireStr(body, "slot_number");
        if (slots.findByBuildingIdAndSlotNumber(buildingId, number).isPresent()) {
            throw ApiException.badRequest("A slot with this number already exists in this building.");
        }
        ParkingSlot slot = new ParkingSlot();
        slot.setBuilding(building(buildingId));
        slot.setSlotNumber(number);
        applyStatus(slot, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(SlotDto.from(slots.save(slot)));
    }

    @PatchMapping("/slots/{id}/")
    @Transactional
    public SlotDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        ParkingSlot slot = scoped(id);
        if (body.containsKey("slot_number")) {
            String number = Body.requireStr(body, "slot_number");
            slots.findByBuildingIdAndSlotNumber(slot.getBuilding().getId(), number)
                    .filter(other -> !other.getId().equals(slot.getId()))
                    .ifPresent(other -> {
                        throw ApiException.badRequest("A slot with this number already exists in this building.");
                    });
            slot.setSlotNumber(number);
        }
        applyStatus(slot, body);
        return SlotDto.from(slots.save(slot));
    }

    @PutMapping("/slots/{id}/")
    @Transactional
    public SlotDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/slots/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        ParkingSlot slot = scoped(id);
        if (vehicles.existsByParkingSlotId(slot.getId())) {
            throw ApiException.badRequest("Release the vehicle in this slot first.");
        }
        slots.delete(slot);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- layout

    /** The building's saved layout, or the spec default — what the grid screen draws from. */
    @GetMapping("/layout/")
    @Transactional(readOnly = true)
    public Map<String, Object> layout(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return DEFAULT_LAYOUT;
        }
        return readLayout(scope.getFirst());
    }

    /**
     * Generates a rows × columns grid: clamps each side to 1..12, get-or-creates
     * {@code {prefix}{row}-{col:02d}} (existing slots keep their status), and saves the layout as
     * the {@code parking_layout} building setting (spec §8.21).
     */
    @PostMapping("/layout/")
    @Transactional
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.asLong(body, "building_id");
        if (buildingId == null) {
            throw ApiException.badRequest("building_id is required");
        }
        tenancy.requireAccess(caller, buildingId);
        Building building = building(buildingId);

        int rows = clampSide(Body.asInt(body, "rows"), 4);
        int columns = clampSide(Body.asInt(body, "columns"), 6);
        String prefix = Body.str(body, "prefix");
        prefix = prefix == null || prefix.isBlank() ? "P" : prefix.trim();
        if (prefix.length() > 40) {
            throw ApiException.badRequest("prefix is too long");
        }

        List<SlotDto> touched = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= columns; column++) {
                String number = "%s%d-%02d".formatted(prefix, row, column);
                ParkingSlot slot = slots.findByBuildingIdAndSlotNumber(buildingId, number).orElseGet(() -> {
                    ParkingSlot created = new ParkingSlot();
                    created.setBuilding(building);
                    created.setSlotNumber(number);
                    created.setStatus(ParkingSlot.AVAILABLE);
                    return slots.save(created);
                });
                touched.add(SlotDto.from(slot));
            }
        }

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("rows", rows);
        layout.put("columns", columns);
        layout.put("prefix", prefix);
        BuildingSetting setting = settings.findByBuildingIdAndKeyName(buildingId, BuildingSetting.PARKING_LAYOUT)
                .orElseGet(() -> {
                    BuildingSetting created = new BuildingSetting();
                    created.setBuilding(building);
                    created.setKeyName(BuildingSetting.PARKING_LAYOUT);
                    return created;
                });
        setting.setValueJson(json.writeValueAsString(layout));
        settings.save(setting);
        activity.record(caller, "building", buildingId, "parking_layout", layout);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("layout", layout);
        out.put("slots", touched);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    /** Shared with the dashboard's {@code parking_layout} section. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readLayout(Long buildingId) {
        return settings.findByBuildingIdAndKeyName(buildingId, BuildingSetting.PARKING_LAYOUT)
                .map(BuildingSetting::getValueJson)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> {
                    try {
                        return (Map<String, Object>) json.readValue(value, Map.class);
                    } catch (JacksonException malformed) {
                        return DEFAULT_LAYOUT;
                    }
                })
                .orElse(DEFAULT_LAYOUT);
    }

    private void applyStatus(ParkingSlot slot, Map<String, Object> body) {
        if (!body.containsKey("status")) {
            return;
        }
        String status = Body.str(body, "status");
        Body.requireOneOf(status, ParkingSlot.STATUSES, "status");
        boolean parked = slot.getId() != null && vehicles.existsByParkingSlotId(slot.getId());
        if (parked && !ParkingSlot.OCCUPIED.equals(status)) {
            throw ApiException.badRequest("A vehicle is parked here — release it before changing the slot's status.");
        }
        slot.setStatus(status);
    }

    private static int clampSide(Integer requested, int fallback) {
        return Math.clamp(requested == null ? fallback : requested, MIN_SIDE, MAX_SIDE);
    }

    private Building building(Long id) {
        return buildings.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private ParkingSlot scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return slots.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
