package com.nibash.poll;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/polls/} — CommitteeOrAdmin for management, but <b>voting is open to any authenticated
 * caller</b> (spec §8.9): residents must be able to vote on polls they cannot edit.
 */
@RestController
@RequestMapping("/api/polls")
public class PollController {

    private final PollRepository polls;
    private final PollOptionRepository options;
    private final VoteRepository votes;
    private final ResidentRepository residents;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public PollController(PollRepository polls, PollOptionRepository options, VoteRepository votes,
                          ResidentRepository residents, BuildingRepository buildings, TenantService tenancy) {
        this.polls = polls;
        this.options = options;
        this.votes = votes;
        this.residents = residents;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record OptionDto(Long id, Long poll, String optionText, long votes, BigDecimal percentage) {

        public static OptionDto from(PollOption option, long count, BigDecimal percentage) {
            return new OptionDto(option.getId(), option.getPoll().getId(), option.getOptionText(),
                    count, percentage);
        }
    }

    public record PollDto(Long id, Long building, String question, Long createdBy,
                          LocalDateTime startDate, LocalDateTime endDate, boolean isClosed,
                          long totalVotes, List<OptionDto> options) {
    }

    public record VoteDto(Long id, Long poll, Long option, Long resident, LocalDateTime votedAt) {

        public static VoteDto from(Vote v) {
            return new VoteDto(v.getId(), v.getPoll().getId(), v.getOption().getId(),
                    v.getResident().getId(), v.getVotedAt());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<PollDto> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageEnvelope.of(polls.findByBuildingIdIn(scope, pageable), this::toDto);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public PollDto detail(@PathVariable Long id) {
        return toDto(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<PollDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        Poll poll = new Poll();
        poll.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        poll.setCreatedBy(caller);
        poll.setQuestion(Body.requireStr(body, "question"));

        LocalDateTime start = Body.asDateTime(body, "start_date");
        poll.setStartDate(start == null ? Times.now() : start);
        poll.setEndDate(Body.asDateTime(body, "end_date"));

        for (Map<String, Object> option : Body.objectList(body, "options")) {
            PollOption choice = new PollOption();
            choice.setOptionText(Body.requireStr(option, "option_text"));
            poll.addOption(choice);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(polls.save(poll)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public PollDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Poll poll = scoped(id);
        if (body.containsKey("question")) {
            poll.setQuestion(Body.requireStr(body, "question"));
        }
        if (body.containsKey("start_date")) {
            poll.setStartDate(Body.requireDateTime(body, "start_date"));
        }
        if (body.containsKey("end_date")) {
            poll.setEndDate(Body.asDateTime(body, "end_date"));
        }
        return toDto(polls.save(poll));
    }

    @PutMapping("/{id}/")
    @Transactional
    public PollDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        polls.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Cast a ballot (spec §8.9). Open to any authenticated caller, unlike the rest of this resource.
     *
     * <p><b>Anti-spoofing:</b> a non-back-office caller's {@code resident_id} is ignored and replaced
     * with their own resident row in the poll's building. Without that, anyone could vote as anyone
     * else simply by changing a number in the request body.
     *
     * <p>The unique {@code (poll, resident)} constraint is the real guard against double voting; the
     * pre-check only exists to turn it into the contract's message instead of a 500.
     */
    @PostMapping("/{id}/vote/")
    @Transactional
    public ResponseEntity<VoteDto> vote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Poll poll = scoped(id);

        if (poll.isClosed()) {
            throw ApiException.badRequest("Poll has closed");
        }

        Long optionId = Body.requireLong(body, "option_id");
        Long requestedResident = Body.requireLong(body, "resident_id");
        Resident resident = resolveVoter(caller, poll, requestedResident);

        PollOption option = options.findById(optionId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        if (!option.getPoll().getId().equals(poll.getId())) {
            throw ApiException.notFound("Not found.");
        }

        if (votes.existsByPollIdAndResidentId(poll.getId(), resident.getId())) {
            throw ApiException.badRequest("Already voted");
        }

        Vote vote = new Vote();
        vote.setPoll(poll);
        vote.setOption(option);
        vote.setResident(resident);
        vote.setVotedAt(Times.now());

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(VoteDto.from(votes.saveAndFlush(vote)));
        } catch (DataIntegrityViolationException raced) {
            // Two ballots in the same instant: the constraint caught the loser.
            throw ApiException.badRequest("Already voted");
        }
    }

    /** Tallies with percentages (spec §8.9). */
    @GetMapping("/{id}/results/")
    @Transactional(readOnly = true)
    public Map<String, Object> results(@PathVariable Long id) {
        Poll poll = scoped(id);
        Map<Long, Long> counts = tally(poll.getId());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> rows = poll.getOptions().stream()
                .map(option -> {
                    long count = counts.getOrDefault(option.getId(), 0L);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("option_id", option.getId());
                    row.put("text", option.getOptionText());
                    row.put("votes", count);
                    row.put("percentage", percentage(count, total));
                    return row;
                })
                .toList();

        return Map.of("poll_id", poll.getId(), "total_votes", total, "results", rows);
    }

    /**
     * Spec §8.9: back-office may vote on another resident's behalf; everyone else votes as
     * themselves, and a caller with no resident row in this building cannot vote at all.
     */
    private Resident resolveVoter(User caller, Poll poll, Long requestedResident) {
        if (caller.isBackOffice()) {
            return residents.findById(requestedResident)
                    .orElseThrow(() -> ApiException.notFound("Not found."));
        }
        return residents.findByUserIdAndBuildingId(caller.getId(), poll.getBuilding().getId())
                .orElseThrow(() -> ApiException.badRequest("You are not a resident of this building."));
    }

    private Map<Long, Long> tally(Long pollId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : votes.tally(pollId)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static BigDecimal percentage(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private PollDto toDto(Poll poll) {
        Map<Long, Long> counts = tally(poll.getId());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<OptionDto> optionDtos = poll.getOptions().stream()
                .map(option -> {
                    long count = counts.getOrDefault(option.getId(), 0L);
                    return OptionDto.from(option, count, percentage(count, total));
                })
                .toList();

        return new PollDto(poll.getId(), poll.getBuilding().getId(), poll.getQuestion(),
                poll.getCreatedBy().getId(), poll.getStartDate(), poll.getEndDate(),
                poll.isClosed(), total, optionDtos);
    }

    private Poll scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return polls.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
