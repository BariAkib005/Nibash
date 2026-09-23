package com.nibash.asset;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * {@code /api/assets/} — CommitteeOrAdmin (spec §8.16).
 *
 * <p>Each row carries a computed {@code warranty_state} — {@code active}, {@code expiring} (inside
 * {@value #EXPIRING_WITHIN_DAYS} days) or {@code expired} — evaluated in the building's timezone, so
 * every screen that warns about warranties agrees on the same rule.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    static final int EXPIRING_WITHIN_DAYS = 60;

    private final AssetRepository assets;
    private final LiftStatusLogRepository lifts;
    private final BuildingRepository buildings;
    private final TenantService tenancy;
    private final ZoneId zone;

    public AssetController(AssetRepository assets, LiftStatusLogRepository lifts, BuildingRepository buildings,
                           TenantService tenancy, @Value("${nibash.timezone}") String timezone) {
        this.assets = assets;
        this.lifts = lifts;
        this.buildings = buildings;
        this.tenancy = tenancy;
        this.zone = ZoneId.of(timezone);
    }

    public record AssetDto(Long id, Long building, String name, String type, LocalDate purchaseDate,
                           LocalDate warrantyExpiry, String status, String warrantyState) {

        public static AssetDto from(Asset a, LocalDate today) {
            return new AssetDto(a.getId(), a.getBuilding().getId(), a.getName(), a.getType(), a.getPurchaseDate(),
                    a.getWarrantyExpiry(), a.getStatus(), AssetController.warrantyState(a.getWarrantyExpiry(), today));
        }
    }

    /** {@code null} when no warranty date is recorded. */
    public static String warrantyState(LocalDate expiry, LocalDate today) {
        if (expiry == null) {
            return null;
        }
        if (expiry.isBefore(today)) {
            return "expired";
        }
        return expiry.isAfter(today.plusDays(EXPIRING_WITHIN_DAYS)) ? "active" : "expiring";
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AssetDto> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        LocalDate today = LocalDate.now(zone);
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(assets.findByBuildingIdIn(scope, pageable), a -> AssetDto.from(a, today));
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AssetDto detail(@PathVariable Long id) {
        return AssetDto.from(scoped(id), LocalDate.now(zone));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<AssetDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        Asset asset = new Asset();
        asset.setBuilding(buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found.")));
        asset.setName(Body.requireStr(body, "name"));
        asset.setType(Body.requireStr(body, "type"));
        apply(asset, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetDto.from(assets.save(asset), LocalDate.now(zone)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public AssetDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Asset asset = scoped(id);
        if (body.containsKey("name")) {
            asset.setName(Body.requireStr(body, "name"));
        }
        if (body.containsKey("type")) {
            asset.setType(Body.requireStr(body, "type"));
        }
        apply(asset, body);
        return AssetDto.from(assets.save(asset), LocalDate.now(zone));
    }

    @PutMapping("/{id}/")
    @Transactional
    public AssetDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    /** Maintenance rows cascade; lift history does not, so a lift with history is retired instead. */
    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        Asset asset = scoped(id);
        if (lifts.existsByAssetId(asset.getId())) {
            throw ApiException.badRequest("This asset has lift status history — mark it decommissioned instead.");
        }
        assets.delete(asset);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(Asset asset, Map<String, Object> body) {
        if (body.containsKey("purchase_date")) {
            asset.setPurchaseDate(Body.asDate(body, "purchase_date"));
        }
        if (body.containsKey("warranty_expiry")) {
            asset.setWarrantyExpiry(Body.asDate(body, "warranty_expiry"));
        }
        if (asset.getPurchaseDate() != null && asset.getWarrantyExpiry() != null
                && asset.getWarrantyExpiry().isBefore(asset.getPurchaseDate())) {
            throw ApiException.badRequest("warranty_expiry cannot be before purchase_date");
        }
        if (body.containsKey("status")) {
            String status = Body.requireStr(body, "status");
            Body.requireOneOf(status, Asset.STATUSES, "status");
            asset.setStatus(status);
        }
    }

    private Asset scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return assets.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
