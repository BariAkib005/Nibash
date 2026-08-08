package com.nibash.tenancy;

import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.StaffRepository;
import com.nibash.user.User;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Row-level multi-tenancy (spec §6) — the guarantee the whole product rests on.
 *
 * <p>A user's <b>allowed buildings</b> are those where they are the developer, the primary contact,
 * a resident, or a staff member. Back-office callers ({@code is_staff}/{@code is_superuser}) are
 * unrestricted.
 *
 * <p>The result is cached per request in {@link TenantContext} so a request that touches ten
 * entities resolves membership once.
 */
@Service
public class TenantService {

    private final BuildingRepository buildings;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final TenantContext context;

    public TenantService(BuildingRepository buildings, ResidentRepository residents,
                         StaffRepository staff, TenantContext context) {
        this.buildings = buildings;
        this.residents = residents;
        this.staff = staff;
        this.context = context;
    }

    /**
     * Building IDs this caller may see. Back-office gets every building.
     * Computed once per request.
     */
    @Transactional(readOnly = true)
    public List<Long> allowedBuildingIds(User caller) {
        List<Long> cached = context.getAllowedBuildingIds();
        if (cached != null) {
            return cached;
        }

        List<Long> allowed;
        if (caller.isBackOffice()) {
            allowed = buildings.findAllIds();
        } else {
            Set<Long> ids = new LinkedHashSet<>();
            ids.addAll(residents.findBuildingIdsForUser(caller.getId()));
            ids.addAll(staff.findBuildingIdsForUser(caller.getId()));
            ids.addAll(buildings.findIdsOwnedBy(caller.getId()));
            allowed = List.copyOf(ids);
        }

        context.setAllowedBuildingIds(allowed);
        return allowed;
    }

    /**
     * Resolves the effective building filter for a request.
     *
     * <p>A {@code ?building_id=} narrows <b>within</b> the allowed set; asking for a building
     * outside it yields 404, never 403 — we never confirm that a foreign building exists.
     */
    @Transactional(readOnly = true)
    public List<Long> resolveScope(User caller, Long requestedBuildingId) {
        List<Long> allowed = allowedBuildingIds(caller);
        if (requestedBuildingId == null) {
            return allowed;
        }
        if (!allowed.contains(requestedBuildingId)) {
            throw ApiException.notFound("Not found.");
        }
        return List.of(requestedBuildingId);
    }

    /** True when the caller may touch this building at all. */
    @Transactional(readOnly = true)
    public boolean canAccess(User caller, Long buildingId) {
        return buildingId != null && allowedBuildingIds(caller).contains(buildingId);
    }

    /** Throws 404 (not 403) when the building is outside the caller's tenancy. */
    @Transactional(readOnly = true)
    public void requireAccess(User caller, Long buildingId) {
        if (!canAccess(caller, buildingId)) {
            throw ApiException.notFound("Not found.");
        }
    }
}
