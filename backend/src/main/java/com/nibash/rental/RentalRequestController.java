package com.nibash.rental;

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
 * {@code /api/rental-requests/} — IsAuthenticated (spec §8.14). The workflow is
 * {@code pending → approved | rejected} by PATCH; only the listing's owner (or a manager) decides,
 * and the requester may withdraw a pending request by deleting it.
 */
@RestController
@RequestMapping("/api/rental-requests")
public class RentalRequestController {

    private final RentalRequestRepository requests;
    private final ListingRepository listings;
    private final ContractRepository contracts;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public RentalRequestController(RentalRequestRepository requests, ListingRepository listings,
                                   ContractRepository contracts, ResidentRepository residents, TenantService tenancy) {
        this.requests = requests;
        this.listings = listings;
        this.contracts = contracts;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record RequestDto(Long id, Long listing, Long tenant, String status, LocalDateTime requestedAt,
                             String listingTitle, String tenantName, Long tenantUser, Long listerUser) {

        public static RequestDto from(RentalRequest r) {
            return new RequestDto(r.getId(), r.getListing().getId(), r.getTenant().getId(), r.getStatus(),
                    r.getRequestedAt(), r.getListing().getTitle(), r.getTenant().getUser().getName(),
                    r.getTenant().getUser().getId(), r.getListing().getResident().getUser().getId());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<RequestDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "listing_id", required = false) Long listingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = listingId == null
                ? requests.findByListingBuildingIdIn(scope, pageable)
                : requests.findByListingBuildingIdInAndListingId(scope, listingId, pageable);
        return PageEnvelope.of(rows, RequestDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public RequestDto detail(@PathVariable Long id) {
        return RequestDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<RequestDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Listing listing = listings.findByIdAndBuildingIdIn(Body.requireLong(body, "listing"),
                        tenancy.allowedBuildingIds(caller))
                .orElseThrow(() -> ApiException.notFound("Not found."));

        Resident tenant = residents.findByUserIdAndBuildingId(caller.getId(), listing.getBuilding().getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents of this building can request a rental."));
        if (listing.getResident().getId().equals(tenant.getId())) {
            throw ApiException.badRequest("You cannot request your own listing.");
        }
        if (requests.existsByListingIdAndTenantIdAndStatus(listing.getId(), tenant.getId(), RentalRequest.PENDING)) {
            throw ApiException.badRequest("You already have a pending request for this listing.");
        }

        RentalRequest request = new RentalRequest();
        request.setListing(listing);
        request.setTenant(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(RequestDto.from(requests.save(request)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public RequestDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        RentalRequest request = scoped(id);
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, RentalRequest.STATUSES, "status");
            boolean lister = request.getListing().getResident().getUser().getId().equals(caller.getId());
            if (!lister && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
                throw ApiException.forbidden("Only the person who listed the unit can decide on requests.");
            }
            if (!RentalRequest.PENDING.equals(request.getStatus()) && !status.equals(request.getStatus())) {
                throw ApiException.badRequest("This request has already been " + request.getStatus() + ".");
            }
            if (RentalRequest.APPROVED.equals(status)
                    && requests.existsByListingIdAndStatus(request.getListing().getId(), RentalRequest.APPROVED)
                    && !RentalRequest.APPROVED.equals(request.getStatus())) {
                throw ApiException.badRequest("Another request for this listing is already approved.");
            }
            request.setStatus(status);
        }
        return RequestDto.from(requests.save(request));
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
        boolean requester = request.getTenant().getUser().getId().equals(caller.getId());
        if (!requester && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("You can only withdraw your own requests.");
        }
        if (contracts.existsByRequestId(request.getId())) {
            throw ApiException.badRequest("A contract is attached to this request, so it can't be withdrawn.");
        }
        requests.delete(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private RentalRequest scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return requests.findByIdAndListingBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
