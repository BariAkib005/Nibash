package com.nibash.parking;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/vehicles/} — IsAuthenticated (spec §8.21).
 *
 * <p>Residents register and edit their <b>own</b> vehicles; allocating a parking slot is a
 * manager's decision, so only admin/committee may set {@code parking_slot}. Every assignment keeps
 * the slot's status in step: the new slot becomes {@code occupied}, the old one {@code available}.
 */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleRepository vehicles;
    private final ParkingSlotRepository slots;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public VehicleController(VehicleRepository vehicles, ParkingSlotRepository slots,
                             ResidentRepository residents, TenantService tenancy) {
        this.vehicles = vehicles;
        this.slots = slots;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record VehicleDto(Long id, Long resident, Long parkingSlot, String vehicleNumber, String type,
                             LocalDateTime registeredAt, String residentName, String unitNumber,
                             String slotNumber) {

        public static VehicleDto from(Vehicle v) {
            Resident r = v.getResident();
            ParkingSlot slot = v.getParkingSlot();
            return new VehicleDto(v.getId(), r.getId(), slot == null ? null : slot.getId(), v.getVehicleNumber(),
                    v.getType(), v.getRegisteredAt(), r.getUser().getName(),
                    r.getUnit() == null ? null : r.getUnit().getUnitNumber(),
                    slot == null ? null : slot.getSlotNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<VehicleDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "resident_id", required = false) Long residentId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("vehicleNumber"));
        var rows = residentId == null
                ? vehicles.findByResidentBuildingIdIn(scope, pageable)
                : vehicles.findByResidentBuildingIdInAndResidentId(scope, residentId, pageable);
        return PageEnvelope.of(rows, VehicleDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public VehicleDto detail(@PathVariable Long id) {
        return VehicleDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<VehicleDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Resident owner = ownerFor(caller, Body.asLong(body, "resident"), Body.asLong(body, "building"));

        String number = normalise(Body.requireStr(body, "vehicle_number"));
        vehicles.findByVehicleNumberIgnoreCase(number).ifPresent(existing -> {
            throw ApiException.badRequest("A vehicle with this number is already registered.");
        });

        Vehicle vehicle = new Vehicle();
        vehicle.setResident(owner);
        vehicle.setVehicleNumber(number);
        applyType(vehicle, body);
        Vehicle saved = vehicles.save(vehicle);
        applySlot(saved, body, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleDto.from(vehicles.save(saved)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public VehicleDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Vehicle vehicle = editable(id, caller);
        if (body.containsKey("vehicle_number")) {
            String number = normalise(Body.requireStr(body, "vehicle_number"));
            vehicles.findByVehicleNumberIgnoreCase(number)
                    .filter(other -> !other.getId().equals(vehicle.getId()))
                    .ifPresent(other -> {
                        throw ApiException.badRequest("A vehicle with this number is already registered.");
                    });
            vehicle.setVehicleNumber(number);
        }
        applyType(vehicle, body);
        applySlot(vehicle, body, caller);
        return VehicleDto.from(vehicles.save(vehicle));
    }

    @PutMapping("/{id}/")
    @Transactional
    public VehicleDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Vehicle vehicle = editable(id, CurrentUser.require());
        free(vehicle.getParkingSlot());
        vehicles.delete(vehicle);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Moves the vehicle to the requested slot (or none). Only managers allocate bays; a slot that
     * already holds another vehicle is refused rather than double-parked.
     */
    private void applySlot(Vehicle vehicle, Map<String, Object> body, User caller) {
        if (!body.containsKey("parking_slot")) {
            return;
        }
        if (!caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("Only the committee can allocate parking slots.");
        }
        Long slotId = Body.asLong(body, "parking_slot");
        ParkingSlot current = vehicle.getParkingSlot();
        if (slotId == null) {
            free(current);
            vehicle.setParkingSlot(null);
            return;
        }
        if (current != null && current.getId().equals(slotId)) {
            return;
        }
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        ParkingSlot target = slots.findByIdAndBuildingIdIn(slotId, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        if (!target.getBuilding().getId().equals(vehicle.getResident().getBuilding().getId())) {
            throw ApiException.badRequest("That slot is in a different building.");
        }
        vehicles.findFirstByParkingSlotId(target.getId())
                .filter(other -> !other.getId().equals(vehicle.getId()))
                .ifPresent(other -> {
                    throw ApiException.badRequest("Slot " + target.getSlotNumber() + " already holds "
                            + other.getVehicleNumber() + ".");
                });
        free(current);
        target.setStatus(ParkingSlot.OCCUPIED);
        slots.save(target);
        vehicle.setParkingSlot(target);
    }

    private void free(ParkingSlot slot) {
        if (slot != null && ParkingSlot.OCCUPIED.equals(slot.getStatus())) {
            slot.setStatus(ParkingSlot.AVAILABLE);
            slots.save(slot);
        }
    }

    private void applyType(Vehicle vehicle, Map<String, Object> body) {
        if (body.containsKey("type")) {
            String type = Body.requireStr(body, "type");
            Body.requireOneOf(type, Vehicle.TYPES, "type");
            vehicle.setType(type);
        }
    }

    /**
     * Managers register a vehicle for any resident in their buildings; everyone else registers
     * for themselves — a {@code resident} in the body that isn't theirs is ignored.
     */
    private Resident ownerFor(User caller, Long residentId, Long buildingId) {
        boolean manager = caller.isBackOffice() || caller.isAdminOrCommittee();
        if (residentId != null) {
            Resident requested = residents.findByIdAndBuildingIdIn(residentId, tenancy.allowedBuildingIds(caller))
                    .orElseThrow(() -> ApiException.notFound("Not found."));
            if (manager || requested.getUser().getId().equals(caller.getId())) {
                return requested;
            }
        }
        if (buildingId != null) {
            tenancy.requireAccess(caller, buildingId);
            return residents.findByUserIdAndBuildingId(caller.getId(), buildingId)
                    .orElseThrow(() -> ApiException.badRequest("Only residents can register vehicles."));
        }
        return residents.findFirstByUserIdOrderByIdAsc(caller.getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents can register vehicles."));
    }

    /** Owners edit their own vehicles; managers edit any in their buildings. */
    private Vehicle editable(Long id, User caller) {
        Vehicle vehicle = scoped(id);
        boolean owner = vehicle.getResident().getUser().getId().equals(caller.getId());
        if (!owner && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("You can only change your own vehicles.");
        }
        return vehicle;
    }

    /** Plates are compared case-insensitively and stored upper-case, so "dhaka-ga 11" matches "DHAKA-GA 11". */
    private static String normalise(String number) {
        return number.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
    }

    private Vehicle scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return vehicles.findByIdAndResidentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
