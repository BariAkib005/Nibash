package com.nibash.access;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
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

/** {@code /api/access-cards/} — CommitteeOrAdmin (spec §8.20). */
@RestController
@RequestMapping("/api/access-cards")
public class AccessCardController {

    private final AccessCardRepository cards;
    private final ResidentRepository residents;
    private final TenantService tenancy;

    public AccessCardController(AccessCardRepository cards, ResidentRepository residents, TenantService tenancy) {
        this.cards = cards;
        this.residents = residents;
        this.tenancy = tenancy;
    }

    public record AccessCardDto(Long id, Long resident, String cardNumber, LocalDateTime issuedAt,
                                String status, String residentName, String unitNumber) {

        public static AccessCardDto from(AccessCard c) {
            var resident = c.getResident();
            var unit = resident.getUnit();
            return new AccessCardDto(c.getId(), resident.getId(), c.getCardNumber(), c.getIssuedAt(),
                    c.getStatus(), resident.getUser().getName(),
                    unit == null ? null : unit.getUnitNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<AccessCardDto> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("cardNumber"));
        return PageEnvelope.of(cards.findByResidentBuildingIdIn(scope, pageable), AccessCardDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public AccessCardDto detail(@PathVariable Long id) {
        return AccessCardDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<AccessCardDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();

        Resident resident = residents.findById(Body.requireLong(body, "resident"))
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        String cardNumber = Body.requireStr(body, "card_number");
        // The number is globally unique in the schema, so catch the clash with the contract's 400
        // rather than letting a constraint violation surface as a 500.
        cards.findByCardNumber(cardNumber).ifPresent(existing -> {
            throw ApiException.badRequest("A card with this number already exists.");
        });

        AccessCard card = new AccessCard();
        card.setResident(resident);
        card.setCardNumber(cardNumber);
        card.setIssuedAt(LocalDateTime.now());
        apply(card, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccessCardDto.from(cards.save(card)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public AccessCardDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        AccessCard card = scoped(id);
        apply(card, body);
        return AccessCardDto.from(cards.save(card));
    }

    @PutMapping("/{id}/")
    @Transactional
    public AccessCardDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        cards.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(AccessCard card, Map<String, Object> body) {
        if (body.containsKey("status")) {
            String status = Body.str(body, "status");
            Body.requireOneOf(status, AccessCard.STATUSES, "status");
            card.setStatus(status);
        }
    }

    private AccessCard scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return cards.findByIdAndResidentBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
