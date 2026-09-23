package com.nibash.vendor;

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
 * {@code /api/reviews/} — IsAuthenticated; residents review vendors (spec §8.2).
 *
 * <p>Two rules the spec leaves implicit:
 * <ul>
 *   <li>A review is always filed as the <b>caller's own</b> resident row, the same anti-spoofing the
 *       poll vote uses — a {@code resident} in the body is ignored unless it is the caller's.</li>
 *   <li>The vendor's {@code rating} is kept equal to the average of its reviews, so every list that
 *       sorts "best rated" reflects what residents actually said.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviews;
    private final VendorRepository vendors;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public ReviewController(ReviewRepository reviews, VendorRepository vendors,
                            ResidentRepository residents, TenantService tenancy) {
        this.reviews = reviews;
        this.vendors = vendors;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record ReviewDto(Long id, Long vendor, Long resident, Short rating, String comment,
                            LocalDateTime createdAt, String residentName, String vendorName) {

        public static ReviewDto from(Review r) {
            return new ReviewDto(r.getId(), r.getVendor().getId(), r.getResident().getId(), r.getRating(),
                    r.getComment(), r.getCreatedAt(), r.getResident().getUser().getName(),
                    r.getVendor().getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ReviewDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId,
                                        @RequestParam(name = "vendor_id", required = false) Long vendorId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = vendorId == null
                ? reviews.findByResidentBuildingIdIn(scope, pageable)
                : reviews.findByResidentBuildingIdInAndVendorId(scope, vendorId, pageable);
        return PageEnvelope.of(rows, ReviewDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ReviewDto detail(@PathVariable Long id) {
        return ReviewDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ReviewDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        Vendor vendor = vendors.findVisibleById(Body.requireLong(body, "vendor"),
                        allowed.isEmpty() ? List.of(-1L) : allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        Review review = new Review();
        review.setVendor(vendor);
        review.setResident(reviewerFor(caller, vendor, Body.asLong(body, "resident")));
        review.setRating(requireRating(body));
        review.setComment(Body.str(body, "comment"));
        Review saved = reviews.saveAndFlush(review);
        refreshVendorRating(vendor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewDto.from(saved));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ReviewDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Review review = ownReview(id);
        if (body.containsKey("rating")) {
            review.setRating(requireRating(body));
        }
        if (body.containsKey("comment")) {
            review.setComment(Body.str(body, "comment"));
        }
        reviews.saveAndFlush(review);
        refreshVendorRating(review.getVendor());
        return ReviewDto.from(review);
    }

    @PutMapping("/{id}/")
    @Transactional
    public ReviewDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Review review = ownReview(id);
        Vendor vendor = review.getVendor();
        reviews.delete(review);
        reviews.flush();
        refreshVendorRating(vendor);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * The reviewer is the caller's resident row: the one named in the body if it really is theirs,
     * else their row in the vendor's building, else their first row anywhere.
     */
    private Resident reviewerFor(User caller, Vendor vendor, Long requestedResidentId) {
        if (requestedResidentId != null) {
            var requested = residents.findById(requestedResidentId);
            if (requested.isPresent() && requested.get().getUser().getId().equals(caller.getId())) {
                return requested.get();
            }
        }
        if (vendor.getBuilding() != null) {
            var own = residents.findByUserIdAndBuildingId(caller.getId(), vendor.getBuilding().getId());
            if (own.isPresent()) {
                return own.get();
            }
        }
        return residents.findFirstByUserIdOrderByIdAsc(caller.getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents can review vendors."));
    }

    /** Authors edit and delete their own reviews; managers may moderate any in their buildings. */
    private Review ownReview(Long id) {
        User caller = CurrentUser.require();
        Review review = scoped(id);
        boolean author = review.getResident().getUser().getId().equals(caller.getId());
        if (!author && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("You can only change your own reviews.");
        }
        return review;
    }

    private void refreshVendorRating(Vendor vendor) {
        vendor.setRating(reviews.roundedAverage(vendor.getId()));
        vendors.save(vendor);
    }

    private Short requireRating(Map<String, Object> body) {
        Integer rating = Body.asInt(body, "rating");
        if (rating == null) {
            throw ApiException.badRequest("rating is required");
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("rating must be between 1 and 5");
        }
        return rating.shortValue();
    }

    private Review scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return reviews.findByIdAndResidentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
