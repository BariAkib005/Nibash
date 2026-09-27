package com.nibash.rental;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.jobs.NotificationService;
import com.nibash.membership.MembershipService;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.user.Roles;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/rental-applications/} — IsAuthenticated, and always the caller's own. Someone applying
 * from the public flats page usually belongs to no building, so these are keyed on the applicant, not
 * on tenancy. The building decides on them through {@code /api/rental-requests/}.
 */
@RestController
@RequestMapping("/api/rental-applications")
public class RentalApplicationController {

    private final RentalRequestRepository requests;
    private final ListingRepository listings;
    private final ContractRepository contracts;
    private final ResidentRepository residents;
    private final MembershipService membership;
    private final NotificationService notifications;
    private final String appUrl;

    public RentalApplicationController(RentalRequestRepository requests, ListingRepository listings,
                                       ContractRepository contracts, ResidentRepository residents,
                                       MembershipService membership, NotificationService notifications,
                                       @Value("${nibash.app-url:http://127.0.0.1:5173}") String appUrl) {
        this.requests = requests;
        this.listings = listings;
        this.contracts = contracts;
        this.residents = residents;
        this.membership = membership;
        this.notifications = notifications;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /** {@code listing_let}: someone else was approved, so this one will not go ahead. */
    public record ApplicationDto(Long id, Long listing, String listingTitle, String buildingName,
                                 String buildingAddress, BigDecimal rent, LocalDate availableFrom, String status,
                                 LocalDateTime requestedAt, String message, boolean listingLet) {

        static ApplicationDto from(RentalRequest r, boolean listingLet) {
            Listing l = r.getListing();
            return new ApplicationDto(r.getId(), l.getId(), l.getTitle(), l.getBuilding().getName(),
                    l.getBuilding().getAddress(), l.getRent(), l.getAvailableFrom(), r.getStatus(),
                    r.getRequestedAt(), r.getMessage(),
                    listingLet && !RentalRequest.APPROVED.equals(r.getStatus()));
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ApplicationDto> list(@RequestParam(defaultValue = "1") int page) {
        User caller = CurrentUser.require();
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = requests.findByApplicantId(caller.getId(), pageable);
        Set<Long> listingIds = new HashSet<>();
        rows.forEach(r -> listingIds.add(r.getListing().getId()));
        Set<Long> let = listingIds.isEmpty() ? Set.of() : new HashSet<>(requests.findLetListingIds(listingIds));
        return PageEnvelope.of(rows, r -> ApplicationDto.from(r, let.contains(r.getListing().getId())));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ApplicationDto> apply(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Listing listing = listings.findOpenPublic(Body.requireLong(body, "listing"))
                .orElseThrow(() -> ApiException.notFound("This flat isn't listed any more."));
        if (listing.isListedBy(caller)) {
            throw ApiException.badRequest("You cannot request your own listing.");
        }

        Resident tenant = residents.findByUserIdAndBuildingId(caller.getId(), listing.getBuilding().getId())
                .orElse(null);
        if (tenant == null) {
            // They will become a resident if approved, so their account has to be able to be one.
            String conflict = membership.roleConflict(caller, Roles.RESIDENT);
            if (conflict != null) {
                throw ApiException.badRequest(conflict);
            }
        }
        if (requests.existsByListingIdAndApplicantIdAndStatus(listing.getId(), caller.getId(), RentalRequest.PENDING)) {
            throw ApiException.badRequest("You already have a pending request for this flat.");
        }

        RentalRequest request = new RentalRequest();
        request.setListing(listing);
        request.setApplicant(caller);
        request.setTenant(tenant);
        request.setMessage(RentalRequestController.message(body));
        request = requests.save(request);
        notifyLister(listing, caller, request.getMessage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationDto.from(request, false));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        User caller = CurrentUser.require();
        RentalRequest request = requests.findByIdAndApplicantId(id, caller.getId())
                .orElseThrow(() -> ApiException.notFound("Not found."));
        if (!RentalRequest.PENDING.equals(request.getStatus())) {
            throw ApiException.badRequest("Only a pending request can be withdrawn.");
        }
        if (contracts.existsByRequestId(request.getId())) {
            throw ApiException.badRequest("A contract is attached to this request, so it can't be withdrawn.");
        }
        requests.delete(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void notifyLister(Listing listing, User applicant, String message) {
        User lister = listing.lister();
        if (lister == null) {
            return;
        }
        String phone = applicant.getPhone() == null || applicant.getPhone().isBlank() ? "" : " · " + applicant.getPhone();
        notifications.emailLater(lister.getEmail(), "New request for " + listing.getTitle(),
                """
                Hello %s,

                %s (%s%s) has asked to rent %s at %s.
                %s
                Review it in Nibash: %s/app/rentals
                """.formatted(lister.getName(), applicant.getName(), applicant.getEmail(), phone,
                        listing.getTitle(), listing.getBuilding().getName(),
                        message == null ? "" : "\nTheir message:\n" + message + "\n", appUrl));
    }
}
