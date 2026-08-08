package com.nibash.resident;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Dtos.DirectoryEntry;
import com.nibash.common.Dtos.DirectoryResponse;
import com.nibash.common.Dtos.ResidentDto;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/residents/} — CommitteeOrAdmin, and {@code /api/directory/} — any authenticated user. */
@RestController
public class ResidentController {

    /** The directory is capped at 100 rows (spec §8.1). */
    private static final int DIRECTORY_CAP = 100;

    private final ResidentRepository residents;
    private final TenantService tenancy;

    public ResidentController(ResidentRepository residents, TenantService tenancy) {
        this.residents = residents;
        this.tenancy = tenancy;
    }

    @GetMapping("/api/residents/")
    @Transactional(readOnly = true)
    public PageEnvelope<ResidentDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("id"));
        return PageEnvelope.of(residents.findByBuildingIdIn(scope, pageable), ResidentDto::from);
    }

    @GetMapping("/api/residents/{id}/")
    @Transactional(readOnly = true)
    public ResidentDto detail(@PathVariable Long id) {
        return ResidentDto.from(scoped(id));
    }

    @PatchMapping("/api/residents/{id}/")
    @Transactional
    public ResidentDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Resident resident = scoped(id);
        if (body.containsKey("is_owner")) {
            resident.setOwner(Boolean.parseBoolean(String.valueOf(body.get("is_owner"))));
        }
        if (body.containsKey("opt_in")) {
            resident.setOptIn(Boolean.parseBoolean(String.valueOf(body.get("opt_in"))));
        }
        return ResidentDto.from(residents.save(resident));
    }

    @DeleteMapping("/api/residents/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        residents.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * {@code GET /api/directory/} (spec §8.1) — listed residents of the caller's buildings, with an
     * optional search over name / unit number / email. Contact details are withheld unless the
     * resident opted in; that gating lives in {@link DirectoryEntry}.
     */
    @GetMapping("/api/directory/")
    @Transactional(readOnly = true)
    public DirectoryResponse directory(@RequestParam(required = false) String search,
                                       @RequestParam(name = "building_id", required = false) Long buildingId) {
        User caller = CurrentUser.require();
        List<Long> scope = tenancy.resolveScope(caller, buildingId);
        if (scope.isEmpty()) {
            return new DirectoryResponse(0, List.of());
        }
        String term = search == null || search.isBlank() ? null : search.trim();
        List<DirectoryEntry> rows = residents
                .searchDirectory(scope, term, PageRequest.of(0, DIRECTORY_CAP))
                .stream()
                .map(DirectoryEntry::from)
                .toList();
        return new DirectoryResponse(rows.size(), rows);
    }

    private Resident scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return residents.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
