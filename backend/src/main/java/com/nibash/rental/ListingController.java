package com.nibash.rental;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * {@code /api/listings/} — IsAuthenticated (spec §8.14). A listing is posted as the caller's own
 * resident row (managers may post for any resident); only its lister or a manager may edit it.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listings;
    private final RentalRequestRepository requests;
    private final ResidentRepository residents;
    private final UnitRepository units;
    private final TenantService tenancy;

    public ListingController(ListingRepository listings, RentalRequestRepository requests,
                             ResidentRepository residents, UnitRepository units, TenantService tenancy) {
        this.listings = listings;
        this.requests = requests;
        this.residents = residents;
        this.units = units;
        this.tenancy = tenancy;
    }

    public record ListingDto(Long id, Long resident, Long building, Long unit, String title, String description,
                             BigDecimal rent, LocalDate availableFrom, LocalDateTime createdAt,
                             String residentName, String unitNumber, Long listerUser) {

        public static ListingDto from(Listing l) {
            return new ListingDto(l.getId(), l.getResident().getId(), l.getBuilding().getId(),
                    l.getUnit() == null ? null : l.getUnit().getId(), l.getTitle(), l.getDescription(), l.getRent(),
                    l.getAvailableFrom(), l.getCreatedAt(), l.getResident().getUser().getName(),
                    l.getUnit() == null ? null : l.getUnit().getUnitNumber(), l.getResident().getUser().getId());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ListingDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(listings.findByBuildingIdIn(scope, pageable), ListingDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ListingDto detail(@PathVariable Long id) {
        return ListingDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ListingDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Resident lister = listerFor(caller, Body.asLong(body, "resident"), Body.asLong(body, "building"));

        Listing listing = new Listing();
        listing.setResident(lister);
        listing.setBuilding(lister.getBuilding());
        listing.setTitle(Body.requireStr(body, "title"));
        listing.setDescription(Body.requireStr(body, "description"));
        listing.setRent(requireRent(body));
        LocalDate from = Body.asDate(body, "available_from");
        if (from == null) {
            throw ApiException.badRequest("available_from is required");
        }
        listing.setAvailableFrom(from);
        applyUnit(listing, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ListingDto.from(listings.save(listing)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ListingDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Listing listing = editable(id);
        if (body.containsKey("title")) {
            listing.setTitle(Body.requireStr(body, "title"));
        }
        if (body.containsKey("description")) {
            listing.setDescription(Body.requireStr(body, "description"));
        }
        if (body.containsKey("rent")) {
            listing.setRent(requireRent(body));
        }
        if (body.containsKey("available_from")) {
            LocalDate from = Body.asDate(body, "available_from");
            if (from == null) {
                throw ApiException.badRequest("available_from is required");
            }
            listing.setAvailableFrom(from);
        }
        applyUnit(listing, body);
        return ListingDto.from(listings.save(listing));
    }

    @PutMapping("/{id}/")
    @Transactional
    public ListingDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    /** Requests and their contracts go with the listing ({@code ON DELETE CASCADE}). */
    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Listing listing = editable(id);
        if (requests.existsByListingIdAndStatus(listing.getId(), RentalRequest.APPROVED)) {
            throw ApiException.badRequest("This listing has an approved request, so it can't be deleted.");
        }
        listings.delete(listing);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void applyUnit(Listing listing, Map<String, Object> body) {
        if (!body.containsKey("unit")) {
            return;
        }
        Long unitId = Body.asLong(body, "unit");
        if (unitId == null) {
            listing.setUnit(null);
            return;
        }
        Unit unit = units.findById(unitId).orElseThrow(() -> ApiException.notFound("Not found."));
        if (!unit.getBuilding().getId().equals(listing.getBuilding().getId())) {
            throw ApiException.badRequest("That unit is in a different building.");
        }
        listing.setUnit(unit);
    }

    private BigDecimal requireRent(Map<String, Object> body) {
        BigDecimal rent = Body.asDecimal(body, "rent");
        if (rent == null) {
            throw ApiException.badRequest("rent is required");
        }
        if (rent.signum() <= 0) {
            throw ApiException.badRequest("Rent must be positive.");
        }
        return rent;
    }

    private Resident listerFor(User caller, Long residentId, Long buildingId) {
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
                    .orElseThrow(() -> ApiException.badRequest("Only residents can list a unit."));
        }
        return residents.findFirstByUserIdOrderByIdAsc(caller.getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents can list a unit."));
    }

    private Listing editable(Long id) {
        User caller = CurrentUser.require();
        Listing listing = scoped(id);
        boolean lister = listing.getResident().getUser().getId().equals(caller.getId());
        if (!lister && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("You can only change your own listings.");
        }
        return listing;
    }

    private Listing scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return listings.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
