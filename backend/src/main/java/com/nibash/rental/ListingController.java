package com.nibash.rental;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
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
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/listings/} — IsAuthenticated (spec §8.14). A listing is posted as the caller's own
 * resident row (managers may post for any resident, or — with no resident row of their own — for the
 * building itself); only its lister or a manager may edit it. Only managers may publish a listing
 * on the public flats page, since that invites people from outside into the building.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingRepository listings;
    private final RentalRequestRepository requests;
    private final ResidentRepository residents;
    private final UnitRepository units;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public ListingController(ListingRepository listings, RentalRequestRepository requests,
                             ResidentRepository residents, UnitRepository units, BuildingRepository buildings,
                             TenantService tenancy) {
        this.listings = listings;
        this.requests = requests;
        this.residents = residents;
        this.units = units;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    /** {@code resident_name} is the lister's name — for a building listing, the manager who posted it. */
    public record ListingDto(Long id, Long resident, Long building, Long unit, String title, String description,
                             BigDecimal rent, LocalDate availableFrom, LocalDateTime createdAt,
                             String residentName, String unitNumber, Long listerUser, boolean isPublic) {

        public static ListingDto from(Listing l) {
            User lister = l.lister();
            return new ListingDto(l.getId(), l.getResident() == null ? null : l.getResident().getId(),
                    l.getBuilding().getId(), l.getUnit() == null ? null : l.getUnit().getId(), l.getTitle(),
                    l.getDescription(), l.getRent(), l.getAvailableFrom(), l.getCreatedAt(),
                    lister == null ? l.getBuilding().getName() : lister.getName(),
                    l.getUnit() == null ? null : l.getUnit().getUnitNumber(),
                    lister == null ? null : lister.getId(), l.isPublicListing());
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
        Long buildingId = Body.asLong(body, "building");
        Resident lister = listerFor(caller, Body.asLong(body, "resident"), buildingId);

        Listing listing = new Listing();
        listing.setResident(lister);
        listing.setListedBy(caller);
        listing.setBuilding(lister != null ? lister.getBuilding() : buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        if (Body.asBool(body, "is_public")) {
            requirePublisher(caller);
            listing.setPublicListing(true);
        }
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
        if (body.containsKey("is_public")) {
            boolean publish = Body.asBool(body, "is_public");
            if (publish != listing.isPublicListing()) {
                requirePublisher(CurrentUser.require());
                listing.setPublicListing(publish);
            }
        }
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

    /**
     * The resident row the listing is posted as. Null means the building lists it: a manager with no
     * resident row of their own there — typically the owner, who runs the building but doesn't live in it.
     */
    private Resident listerFor(User caller, Long residentId, Long buildingId) {
        boolean manager = isManager(caller);
        if (residentId != null) {
            Resident requested = residents.findByIdAndBuildingIdIn(residentId, tenancy.allowedBuildingIds(caller))
                    .orElseThrow(() -> ApiException.notFound("Not found."));
            if (manager || requested.getUser().getId().equals(caller.getId())) {
                return requested;
            }
        }
        if (buildingId != null) {
            tenancy.requireAccess(caller, buildingId);
            Optional<Resident> own = residents.findByUserIdAndBuildingId(caller.getId(), buildingId);
            if (own.isPresent() || !manager) {
                return own.orElseThrow(() -> ApiException.badRequest("Only residents can list a unit."));
            }
            return null;
        }
        return residents.findFirstByUserIdOrderByIdAsc(caller.getId()).orElseThrow(() ->
                ApiException.badRequest(manager ? "building is required" : "Only residents can list a unit."));
    }

    private static boolean isManager(User caller) {
        return caller.isBackOffice() || caller.isAdminOrCommittee();
    }

    private static void requirePublisher(User caller) {
        if (!isManager(caller)) {
            throw ApiException.forbidden("Only the committee can put a flat on the public flats page.");
        }
    }

    private Listing editable(Long id) {
        User caller = CurrentUser.require();
        Listing listing = scoped(id);
        if (!listing.isListedBy(caller) && !isManager(caller)) {
            throw ApiException.forbidden("You can only change your own listings.");
        }
        return listing;
    }

    private Listing scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return listings.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
