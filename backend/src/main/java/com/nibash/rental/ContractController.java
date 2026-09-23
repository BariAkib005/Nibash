package com.nibash.rental;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.storage.StorageService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/contracts/} — IsAuthenticated, multipart accepted (spec §8.14). A contract attaches to
 * an <b>approved</b> request, one per request, and is filed by the lister or a manager.
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private static final long MAX_CONTRACT_BYTES = 10L * 1024 * 1024;

    private final ContractRepository contracts;
    private final RentalRequestRepository requests;
    private final StorageService storage;
    private final TenantService tenancy;

    public ContractController(ContractRepository contracts, RentalRequestRepository requests,
                              StorageService storage, TenantService tenancy) {
        this.contracts = contracts;
        this.requests = requests;
        this.storage = storage;
        this.tenancy = tenancy;
    }

    public record ContractDto(Long id, Long request, String contractPath, LocalDateTime signedAt,
                              String listingTitle, String tenantName) {

        public static ContractDto from(Contract c) {
            return new ContractDto(c.getId(), c.getRequest().getId(), c.getContractPath(), c.getSignedAt(),
                    c.getRequest().getListing().getTitle(), c.getRequest().getTenant().getUser().getName());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ContractDto> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "signedAt"));
        return PageEnvelope.of(contracts.findByRequestListingBuildingIdIn(scope, pageable), ContractDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ContractDto detail(@PathVariable Long id) {
        return ContractDto.from(scoped(id));
    }

    @PostMapping(value = "/", consumes = "application/json")
    @Transactional
    public ResponseEntity<ContractDto> create(@RequestBody Map<String, Object> body) {
        return create(body, null);
    }

    /** Multipart upload — a separate handler so Spring never tries to parse the form as JSON. */
    @PostMapping(value = "/", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<ContractDto> upload(@RequestParam Map<String, Object> form,
                                              @RequestPart(name = "file", required = false) MultipartFile file) {
        return create(form, file);
    }

    private ResponseEntity<ContractDto> create(Map<String, Object> body, MultipartFile file) {
        User caller = CurrentUser.require();

        RentalRequest request = requests.findByIdAndListingBuildingIdIn(Body.requireLong(body, "request"),
                        tenancy.allowedBuildingIds(caller))
                .orElseThrow(() -> ApiException.notFound("Not found."));
        boolean lister = request.getListing().getResident().getUser().getId().equals(caller.getId());
        if (!lister && !caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("Only the person who listed the unit can file its contract.");
        }
        if (!RentalRequest.APPROVED.equals(request.getStatus())) {
            throw ApiException.badRequest("A contract can only be attached to an approved request.");
        }
        if (contracts.existsByRequestId(request.getId())) {
            throw ApiException.badRequest("This request already has a contract.");
        }

        Contract contract = new Contract();
        contract.setRequest(request);
        if (file != null && !file.isEmpty()) {
            storage.requireAtMost(file, MAX_CONTRACT_BYTES, "file must be 10MB or smaller");
            storage.requireType(file, StorageService.DOCUMENT_TYPES);
            contract.setContractPath(storage.store(file, "contracts"));
        } else {
            contract.setContractPath(Body.requireStr(body, "contract_path"));
        }
        LocalDateTime signedAt = Body.asDateTime(body, "signed_at");
        contract.setSignedAt(signedAt == null ? Times.now() : Times.toStorage(signedAt));
        return ResponseEntity.status(HttpStatus.CREATED).body(ContractDto.from(contracts.save(contract)));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User caller = CurrentUser.require();
        Contract contract = scoped(id);
        if (!caller.isBackOffice() && !caller.isAdminOrCommittee()) {
            throw ApiException.forbidden("Only the committee can remove a signed contract.");
        }
        contracts.delete(contract);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Contract scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return contracts.findByIdAndRequestListingBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
