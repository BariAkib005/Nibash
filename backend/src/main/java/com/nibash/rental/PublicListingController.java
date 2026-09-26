package com.nibash.rental;

import com.nibash.common.ApiException;
import com.nibash.common.PageEnvelope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/public/listings/} — AllowAny. The flats page for people who don't live in any building
 * yet: every published listing that is still open. It carries the flat and its building, never who
 * listed it or anything else about the building's people.
 */
@RestController
@RequestMapping("/api/public/listings")
public class PublicListingController {

    private final ListingRepository listings;

    public PublicListingController(ListingRepository listings) {
        this.listings = listings;
    }

    public record PublicListingDto(Long id, String title, String description, BigDecimal rent,
                                   LocalDate availableFrom, LocalDateTime createdAt, String buildingName,
                                   String buildingAddress, String unitNumber, String unitType, BigDecimal sizeSqft,
                                   Integer floor) {

        public static PublicListingDto from(Listing l) {
            var unit = l.getUnit();
            return new PublicListingDto(l.getId(), l.getTitle(), l.getDescription(), l.getRent(),
                    l.getAvailableFrom(), l.getCreatedAt(), l.getBuilding().getName(), l.getBuilding().getAddress(),
                    unit == null ? null : unit.getUnitNumber(), unit == null ? null : unit.getType(),
                    unit == null ? null : unit.getSizeSqft(), unit == null ? null : unit.getFloor());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<PublicListingDto> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(name = "max_rent", required = false) BigDecimal maxRent) {
        String term = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(listings.searchPublic(term, maxRent, pageable), PublicListingDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public PublicListingDto detail(@PathVariable Long id) {
        return PublicListingDto.from(listings.findOpenPublic(id)
                .orElseThrow(() -> ApiException.notFound("This flat isn't listed any more.")));
    }
}
