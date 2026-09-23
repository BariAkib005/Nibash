package com.nibash.vendor;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/vendors/} — CommitteeOrAdmin; visibility is own-building plus global (spec §8.2).
 *
 * <p>A global vendor ({@code building = null}) shows up in every tenant's catalog, so only a
 * back-office operator may create or edit one — otherwise one building's committee could rewrite
 * a listing every other building sees.
 */
@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    /** Mean Earth radius the spec fixes for the Haversine distance (§8.2). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final VendorRepository vendors;
    private final ServiceRepository services;
    private final ReviewRepository reviews;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public VendorController(VendorRepository vendors, ServiceRepository services, ReviewRepository reviews,
                            BuildingRepository buildings, TenantService tenancy) {
        this.vendors = vendors;
        this.services = services;
        this.reviews = reviews;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    /** {@code distance_km} is only populated by the nearby search. */
    public record VendorDto(Long id, Long service, Long building, String name, String contactInfo,
                            BigDecimal rating, BigDecimal latitude, BigDecimal longitude,
                            LocalDateTime createdAt, String serviceName, Double distanceKm) {

        public static VendorDto from(Vendor v) {
            return from(v, null);
        }

        public static VendorDto from(Vendor v, Double distanceKm) {
            return new VendorDto(v.getId(), v.getService().getId(),
                    v.getBuilding() == null ? null : v.getBuilding().getId(),
                    v.getName(), v.getContactInfo(), v.getRating(), v.getLatitude(), v.getLongitude(),
                    v.getCreatedAt(), v.getService().getName(), distanceKm);
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<VendorDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId,
                                        @RequestParam(name = "service_id", required = false) Long serviceId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        // An empty IN () is invalid SQL; a caller with no building still sees global vendors.
        List<Long> ids = scope.isEmpty() ? List.of(-1L) : scope;
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "rating").and(Sort.by("name")));
        return PageEnvelope.of(vendors.findVisible(ids, serviceId, pageable), VendorDto::from);
    }

    /**
     * {@code GET /api/vendors/nearby/?service_id=&lat=&lng=&radius_km=5} — Haversine over the
     * visible, geolocated vendors of one service; inside the radius, nearest first (spec §8.2).
     */
    @GetMapping("/nearby/")
    @Transactional(readOnly = true)
    public Map<String, Object> nearby(@RequestParam(name = "service_id", required = false) String serviceId,
                                      @RequestParam(required = false) String lat,
                                      @RequestParam(required = false) String lng,
                                      @RequestParam(name = "radius_km", defaultValue = "5") String radiusKm) {
        if (isBlank(serviceId) || isBlank(lat) || isBlank(lng)) {
            throw ApiException.badRequest("service_id, lat, lng are required");
        }
        long service;
        double originLat;
        double originLng;
        double radius;
        try {
            service = Long.parseLong(serviceId.trim());
            originLat = Double.parseDouble(lat.trim());
            originLng = Double.parseDouble(lng.trim());
            radius = Double.parseDouble(radiusKm.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("service_id, lat, lng and radius_km must be numbers");
        }

        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        List<Long> ids = allowed.isEmpty() ? List.of(-1L) : allowed;

        List<VendorDto> results = vendors.findNearbyCandidates(service, ids).stream()
                .map(v -> VendorDto.from(v, round2(haversineKm(originLat, originLng,
                        v.getLatitude().doubleValue(), v.getLongitude().doubleValue()))))
                .filter(dto -> dto.distanceKm() <= radius)
                .sorted(Comparator.comparingDouble(VendorDto::distanceKm))
                .toList();
        return Map.of("count", results.size(), "results", results);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public VendorDto detail(@PathVariable Long id) {
        return VendorDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<VendorDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Vendor vendor = new Vendor();
        vendor.setService(services.findById(Body.requireLong(body, "service"))
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        vendor.setName(Body.requireStr(body, "name"));

        Long buildingId = Body.asLong(body, "building");
        if (buildingId == null) {
            requireBackOfficeForGlobal(caller);
        } else {
            tenancy.requireAccess(caller, buildingId);
            vendor.setBuilding(building(buildingId));
        }
        apply(vendor, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendorDto.from(vendors.save(vendor)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public VendorDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Vendor vendor = scoped(id);
        if (vendor.getBuilding() == null) {
            requireBackOfficeForGlobal(caller);
        }
        if (body.containsKey("name")) {
            vendor.setName(Body.requireStr(body, "name"));
        }
        if (body.containsKey("service")) {
            vendor.setService(services.findById(Body.requireLong(body, "service"))
                    .orElseThrow(() -> ApiException.notFound("Not found.")));
        }
        apply(vendor, body);
        return VendorDto.from(vendors.save(vendor));
    }

    @PutMapping("/{id}/")
    @Transactional
    public VendorDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        Vendor vendor = scoped(id);
        if (vendor.getBuilding() == null) {
            requireBackOfficeForGlobal(CurrentUser.require());
        }
        if (reviews.countByVendorId(vendor.getId()) > 0) {
            throw ApiException.badRequest("This vendor has reviews, so it can't be deleted.");
        }
        vendors.delete(vendor);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(Vendor vendor, Map<String, Object> body) {
        if (body.containsKey("contact_info")) {
            vendor.setContactInfo(Body.str(body, "contact_info"));
        }
        if (body.containsKey("rating")) {
            BigDecimal rating = Body.asDecimal(body, "rating");
            if (rating != null && (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0)) {
                throw ApiException.badRequest("rating must be between 0 and 5");
            }
            vendor.setRating(rating == null ? null : rating.setScale(1, RoundingMode.HALF_UP));
        }
        if (body.containsKey("latitude")) {
            BigDecimal latitude = Body.asDecimal(body, "latitude");
            if (latitude != null && latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0) {
                throw ApiException.badRequest("latitude must be between -90 and 90");
            }
            vendor.setLatitude(latitude);
        }
        if (body.containsKey("longitude")) {
            BigDecimal longitude = Body.asDecimal(body, "longitude");
            if (longitude != null && longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
                throw ApiException.badRequest("longitude must be between -180 and 180");
            }
            vendor.setLongitude(longitude);
        }
    }

    private void requireBackOfficeForGlobal(User caller) {
        if (!caller.isBackOffice()) {
            throw ApiException.forbidden("Only platform operators can manage global vendors.");
        }
    }

    private Building building(Long id) {
        return buildings.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private Vendor scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return vendors.findVisibleById(id, allowed.isEmpty() ? List.of(-1L) : allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
