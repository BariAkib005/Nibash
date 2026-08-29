package com.nibash.poll;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.PageEnvelope;
import com.nibash.poll.PollController.VoteDto;
import com.nibash.tenancy.TenantService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/votes/} — IsAuthenticated, read-only (spec §8.9). Ballots are cast through
 * {@code POST /api/polls/{id}/vote/}, which is where the anti-spoofing and one-vote rules live;
 * exposing a generic create here would be a way around both.
 */
@RestController
@RequestMapping("/api/votes")
public class VoteController {

    private final VoteRepository votes;
    private final TenantService tenancy;

    public VoteController(VoteRepository votes, TenantService tenancy) {
        this.votes = votes;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<VoteDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "votedAt"));
        return PageEnvelope.of(votes.findByPollBuildingIdIn(scope, pageable), VoteDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public VoteDto detail(@PathVariable Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return VoteDto.from(votes.findByIdAndPollBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
    }
}
