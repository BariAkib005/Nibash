package com.nibash.asset;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.vendor.Vendor;
import com.nibash.vendor.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/asset-maintenance/} — CommitteeOrAdmin (spec §8.16). Filter: {@code ?asset_id=}. */
@RestController
@RequestMapping("/api/asset-maintenance")
public class AssetMaintenanceController {

    private final AssetMaintenanceRepository maintenance;
    private final AssetRepository assets;
    private final VendorRepository vendors;
    private final TenantService tenancy;

    public AssetMaintenanceController(AssetMaintenanceRepository maintenance, AssetRepository assets,
                                      VendorRepository vendors, TenantService tenancy) {
        this.maintenance = maintenance;
        this.assets = assets;
        this.vendors = vendors;
        this.tenancy = tenancy;
    }

    public record MaintenanceDto(Long id, Long asset, LocalDate scheduledDate, LocalDate completedDate,
                                 String description, BigDecimal cost, Long vendor, String assetName,
                                 String vendorName) {

        public static MaintenanceDto from(AssetMaintenance m) {
            Vendor vendor = m.getVendor();
            return new MaintenanceDto(m.getId(), m.getAsset().getId(), m.getScheduledDate(), m.getCompletedDate(),
                    m.getDescription(), m.getCost(), vendor == null ? null : vendor.getId(), m.getAsset().getName(),
                    vendor == null ? null : vendor.getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<MaintenanceDto> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(name = "building_id", required = false) Long buildingId,
                                             @RequestParam(name = "asset_id", required = false) Long assetId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "scheduledDate").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = assetId == null
                ? maintenance.findByAssetBuildingIdIn(scope, pageable)
                : maintenance.findByAssetBuildingIdInAndAssetId(scope, assetId, pageable);
        return PageEnvelope.of(rows, MaintenanceDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public MaintenanceDto detail(@PathVariable Long id) {
        return MaintenanceDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<MaintenanceDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        Asset asset = assets.findByIdAndBuildingIdIn(Body.requireLong(body, "asset"), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        AssetMaintenance row = new AssetMaintenance();
        row.setAsset(asset);
        LocalDate scheduled = Body.asDate(body, "scheduled_date");
        if (scheduled == null) {
            throw ApiException.badRequest("scheduled_date is required");
        }
        row.setScheduledDate(scheduled);
        apply(row, body, allowed);
        return ResponseEntity.status(HttpStatus.CREATED).body(MaintenanceDto.from(maintenance.save(row)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public MaintenanceDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        AssetMaintenance row = scoped(id);
        if (body.containsKey("scheduled_date")) {
            LocalDate scheduled = Body.asDate(body, "scheduled_date");
            if (scheduled == null) {
                throw ApiException.badRequest("scheduled_date is required");
            }
            row.setScheduledDate(scheduled);
        }
        apply(row, body, allowed);
        return MaintenanceDto.from(maintenance.save(row));
    }

    @PutMapping("/{id}/")
    @Transactional
    public MaintenanceDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        maintenance.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(AssetMaintenance row, Map<String, Object> body, List<Long> allowed) {
        if (body.containsKey("completed_date")) {
            row.setCompletedDate(Body.asDate(body, "completed_date"));
        }
        if (body.containsKey("description")) {
            row.setDescription(Body.str(body, "description"));
        }
        if (body.containsKey("cost")) {
            BigDecimal cost = Body.asDecimal(body, "cost");
            if (cost != null && cost.signum() < 0) {
                throw ApiException.badRequest("cost cannot be negative");
            }
            row.setCost(cost);
        }
        if (body.containsKey("vendor")) {
            Long vendorId = Body.asLong(body, "vendor");
            row.setVendor(vendorId == null ? null
                    : vendors.findVisibleById(vendorId, allowed.isEmpty() ? List.of(-1L) : allowed)
                            .orElseThrow(() -> ApiException.notFound("Not found.")));
        }
    }

    private AssetMaintenance scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return maintenance.findByIdAndAssetBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
