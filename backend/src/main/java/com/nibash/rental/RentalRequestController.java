package com.nibash.rental;

import com.nibash.activity.ActivityLogService;
import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.jobs.NotificationService;
import com.nibash.membership.MembershipService;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.unit.Unit;
import com.nibash.unit.UnitRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/rental-requests/} — IsAuthenticated (spec §8.14). The workflow is
 * {@code pending → approved | rejected} by PATCH; only the listing's owner (or a manager) decides,
 * and the requester may withdraw a pending request by deleting it.
 *
 * <p>Requests from outside the building arrive through the public flats page
 * ({@link RentalApplicationController}) and show up here for the building to decide. Approving one
 * makes the applicant a resident of the flat, so only a manager may approve it; the lister may still
 * decline. Residents see only the requests they made or received; managers see all of them.
 */
@RestController
@RequestMapping("/api/rental-requests")
public class RentalRequestController {

    static final int MESSAGE_LIMIT = 1000;

    private final RentalRequestRepository requests;
    private final ListingRepository listings;
    private final ContractRepository contracts;
    private final ResidentRepository residents;
    private final UnitRepository units;
    private final MembershipService membership;
    private final TenantService tenancy;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final String appUrl;

    public RentalRequestController(RentalRequestRepository requests, ListingRepository listings,
                                   ContractRepository contracts, ResidentRepository residents, UnitRepository units,
                                   MembershipService membership, TenantService tenancy,
                                   NotificationService notifications, ActivityLogService activity,
                                   @Value("${nibash.app-url:http://127.0.0.1:5173}") String appUrl) {
        this.requests = requests;
        this.listings = listings;
        this.contracts = contracts;
        this.residents = residents;
        this.units = units;
        this.membership = membership;
        this.tenancy = tenancy;
        this.notifications = notifications;
        this.activity = activity;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /**
     * {@code tenant_name}/{@code tenant_user} are the applicant's. Contact details are filled in only
     * for the people deciding (the lister and managers) and for the applicant themself.
     */
    public record RequestDto(Long id, Long listing, Long tenant, String status, LocalDateTime requestedAt,
                             String listingTitle, String tenantName, Long tenantUser, Long listerUser,
                             String message, boolean outsideApplicant, String applicantEmail, String applicantPhone) {

        public static RequestDto from(RentalRequest r, User viewer) {
            User applicant = r.getApplicant();
            User lister = r.getListing().lister();
            boolean seesContact = viewer.isBackOffice() || viewer.isAdminOrCommittee()
                    || r.getListing().isListedBy(viewer) || applicant.getId().equals(viewer.getId());
            return new RequestDto(r.getId(), r.getListing().getId(),
                    r.getTenant() == null ? null : r.getTenant().getId(), r.getStatus(), r.getRequestedAt(),
                    r.getListing().getTitle(), applicant.getName(), applicant.getId(),
                    lister == null ? null : lister.getId(), r.getMessage(), r.getTenant() == null,
                    seesContact ? applicant.getEmail() : null,
                    seesContact ? blankToNull(applicant.getPhone()) : null);
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<RequestDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "listing_id", required = false) Long listingId) {
        User caller = CurrentUser.require();
        List<Long> scope = tenancy.resolveScope(caller, buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = !isManager(caller)
                ? requests.findForParty(scope, listingId, caller.getId(), pageable)
                : listingId == null
                        ? requests.findByListingBuildingIdIn(scope, pageable)
                        : requests.findByListingBuildingIdInAndListingId(scope, listingId, pageable);
        return PageEnvelope.of(rows, r -> RequestDto.from(r, caller));
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public RequestDto detail(@PathVariable Long id) {
        User caller = CurrentUser.require();
        RentalRequest request = scoped(id);
        if (!isManager(caller) && !isParty(request, caller)) {
            throw ApiException.notFound("Not found.");
        }
        return RequestDto.from(request, caller);
    }

    /** A neighbour asking about a flat in their own building. */
    @PostMapping("/")
    @Transactional
    public ResponseEntity<RequestDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Listing listing = listings.findByIdAndBuildingIdIn(Body.requireLong(body, "listing"),
                        tenancy.allowedBuildingIds(caller))
                .orElseThrow(() -> ApiException.notFound("Not found."));

        Resident tenant = residents.findByUserIdAndBuildingId(caller.getId(), listing.getBuilding().getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents of this building can request a rental."));
        if (listing.isListedBy(caller)
                || (listing.getResident() != null && listing.getResident().getId().equals(tenant.getId()))) {
            throw ApiException.badRequest("You cannot request your own listing.");
        }
        if (requests.existsByListingIdAndApplicantIdAndStatus(listing.getId(), caller.getId(), RentalRequest.PENDING)) {
            throw ApiException.badRequest("You already have a pending request for this listing.");
        }
        if (requests.existsByListingIdAndStatus(listing.getId(), RentalRequest.APPROVED)) {
            throw ApiException.badRequest("This flat has already been let.");
        }

        RentalRequest request = new RentalRequest();
        request.setListing(listing);
        request.setApplicant(caller);
        request.setTenant(tenant);
        request.setMessage(message(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(RequestDto.from(requests.save(request), caller));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public RequestDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        RentalRequest request = scoped(id);
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, RentalRequest.STATUSES, "status");
            if (!request.getListing().isListedBy(caller) && !isManager(caller)) {
                throw ApiException.forbidden("Only the person who listed the unit can decide on requests.");
            }
            if (!RentalRequest.PENDING.equals(request.getStatus()) && !status.equals(request.getStatus())) {
                throw ApiException.badRequest("This request has already been " + request.getStatus() + ".");
            }
            boolean approving = RentalRequest.APPROVED.equals(status)
                    && !RentalRequest.APPROVED.equals(request.getStatus());
            if (approving && requests.existsByListingIdAndStatus(request.getListing().getId(), RentalRequest.APPROVED)) {
                throw ApiException.badRequest("Another request for this listing is already approved.");
            }
            if (approving && request.getTenant() == null) {
                if (!isManager(caller)) {
                    throw ApiException.forbidden("Only the committee can approve someone from outside the building, "
                            + "because approving makes them a resident.");
                }
                request.setTenant(admit(request));
            }
            boolean changed = !status.equals(request.getStatus());
            request.setStatus(status);
            if (changed) {
                tell(request);
                activity.record(caller, "rental_request", request.getId(), status,
                        Map.of("listing", request.getListing().getTitle(), "applicant", request.getApplicant().getName()));
            }
        }
        return RequestDto.from(requests.save(request), caller);
    }

    @PutMapping("/{id}/")
    @Transactional
    public RequestDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User caller = CurrentUser.require();
        RentalRequest request = scoped(id);
        boolean requester = request.getApplicant().getId().equals(caller.getId());
        if (!requester && !isManager(caller)) {
            throw ApiException.forbidden("You can only withdraw your own requests.");
        }
        if (contracts.existsByRequestId(request.getId())) {
            throw ApiException.badRequest("A contract is attached to this request, so it can't be withdrawn.");
        }
        requests.delete(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Moves an approved outsider in: a resident row for the listed flat from its available date, the
     * flat marked rented. Reuses a resident row they already have here (say, from an invitation).
     */
    private Resident admit(RentalRequest request) {
        User applicant = request.getApplicant();
        Listing listing = request.getListing();
        Building building = listing.getBuilding();
        Resident existing = residents.findByUserIdAndBuildingId(applicant.getId(), building.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        String conflict = membership.roleConflict(applicant, Roles.RESIDENT);
        if (conflict != null) {
            throw ApiException.badRequest(conflict);
        }
        membership.adoptRole(applicant, Roles.RESIDENT);
        Unit unit = listing.getUnit();
        Resident resident = membership.addResident(applicant, building, unit, false, listing.getAvailableFrom());
        if (unit != null) {
            unit.setStatus(Unit.RENTED);
            units.save(unit);
        }
        return resident;
    }

    /** The applicant hears the outcome by email when SMTP is configured; failures are silent. */
    private void tell(RentalRequest request) {
        Listing listing = request.getListing();
        String where = listing.getTitle() + " at " + listing.getBuilding().getName();
        if (RentalRequest.APPROVED.equals(request.getStatus())) {
            notifications.emailLater(request.getApplicant().getEmail(), "Your rental request was approved",
                    """
                    Hello %s,

                    Good news: your request for %s has been approved.
                    Sign in to Nibash to see your building: %s/login
                    """.formatted(request.getApplicant().getName(), where, appUrl));
        } else if (RentalRequest.REJECTED.equals(request.getStatus())) {
            notifications.emailLater(request.getApplicant().getEmail(), "Your rental request",
                    """
                    Hello %s,

                    Thank you for your interest in %s. The building has decided not to go ahead with your
                    request this time. Other flats are listed at %s/flats
                    """.formatted(request.getApplicant().getName(), where, appUrl));
        }
    }

    static String message(Map<String, Object> body) {
        String message = blankToNull(Body.str(body, "message"));
        if (message != null && message.length() > MESSAGE_LIMIT) {
            throw ApiException.badRequest("Keep the message under " + MESSAGE_LIMIT + " characters.");
        }
        return message;
    }

    private static boolean isParty(RentalRequest request, User caller) {
        return request.getApplicant().getId().equals(caller.getId()) || request.getListing().isListedBy(caller);
    }

    private static boolean isManager(User caller) {
        return caller.isBackOffice() || caller.isAdminOrCommittee();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RentalRequest scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return requests.findByIdAndListingBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
