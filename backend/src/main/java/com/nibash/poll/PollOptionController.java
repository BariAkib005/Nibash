package com.nibash.poll;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.poll.PollController.OptionDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.nibash.tenancy.TenantService;

/**
 * {@code /api/options/} — CommitteeOrAdmin (spec §8.9). Options are usually created nested inside a
 * poll; this resource exists to add or rename one afterwards.
 */
@RestController
@RequestMapping("/api/options")
public class PollOptionController {

    private final PollOptionRepository options;
    private final PollRepository polls;
    private final VoteRepository votes;
    private final TenantService tenancy;

    public PollOptionController(PollOptionRepository options, PollRepository polls,
                                VoteRepository votes, TenantService tenancy) {
        this.options = options;
        this.polls = polls;
        this.votes = votes;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<OptionDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        return PageEnvelope.of(options.findByPollBuildingIdIn(scope, pageable), this::toDto);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public OptionDto detail(@PathVariable Long id) {
        return toDto(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<OptionDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        Poll poll = polls.findByIdAndBuildingIdIn(Body.requireLong(body, "poll"), allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        PollOption option = new PollOption();
        option.setPoll(poll);
        option.setOptionText(Body.requireStr(body, "option_text"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(options.save(option)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public OptionDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        PollOption option = scoped(id);
        if (body.containsKey("option_text")) {
            option.setOptionText(Body.requireStr(body, "option_text"));
        }
        return toDto(options.save(option));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        options.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private OptionDto toDto(PollOption option) {
        long total = votes.countByPollId(option.getPoll().getId());
        long count = votes.tally(option.getPoll().getId()).stream()
                .filter(row -> option.getId().equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(0L);
        BigDecimal percentage = total == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        return OptionDto.from(option, count, percentage);
    }

    private PollOption scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return options.findByIdAndPollBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
