package com.nibash.notice;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/notices/} — CommitteeOrAdmin (spec §8.4). */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeRepository notices;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public NoticeController(NoticeRepository notices, BuildingRepository buildings, TenantService tenancy) {
        this.notices = notices;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record NoticeDto(Long id, Long building, String title, String body, boolean isPinned,
                            LocalDateTime publishDate, LocalDateTime expiryDate, Long createdBy,
                            LocalDateTime createdAt, String createdByName) {

        public static NoticeDto from(Notice n) {
            return new NoticeDto(n.getId(), n.getBuilding().getId(), n.getTitle(), n.getBody(),
                    n.isPinned(), n.getPublishDate(), n.getExpiryDate(), n.getCreatedBy().getId(),
                    n.getCreatedAt(), n.getCreatedBy().getName());
        }
    }

    /**
     * The board (spec §8.4). By default only <b>live</b> notices are returned — published already
     * and not yet expired — because a noticeboard showing next month's notice, or last year's, is
     * worse than useless. {@code ?include_archived=true} lifts that for the admin view.
     */
    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<NoticeDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId,
                                        @RequestParam(name = "include_archived", defaultValue = "false") boolean includeArchived,
                                        @RequestParam(required = false) String search) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        LocalDateTime now = includeArchived ? null : Times.now();
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageEnvelope.of(notices.board(scope, now, term, pageable), NoticeDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public NoticeDto detail(@PathVariable Long id) {
        return NoticeDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<NoticeDto> create(@RequestBody Map<String, Object> requestBody) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(requestBody, "building");
        tenancy.requireAccess(caller, buildingId);

        Notice notice = new Notice();
        notice.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        notice.setCreatedBy(caller);
        notice.setTitle(Body.requireStr(requestBody, "title"));
        notice.setBody(Body.requireStr(requestBody, "body"));

        LocalDateTime publish = Body.asDateTime(requestBody, "publish_date");
        notice.setPublishDate(publish == null ? Times.now() : publish);
        apply(notice, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(NoticeDto.from(notices.save(notice)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public NoticeDto update(@PathVariable Long id, @RequestBody Map<String, Object> requestBody) {
        Policy.requireManager();
        Notice notice = scoped(id);
        if (requestBody.containsKey("title")) {
            notice.setTitle(Body.requireStr(requestBody, "title"));
        }
        if (requestBody.containsKey("body")) {
            notice.setBody(Body.requireStr(requestBody, "body"));
        }
        if (requestBody.containsKey("publish_date")) {
            notice.setPublishDate(Body.requireDateTime(requestBody, "publish_date"));
        }
        apply(notice, requestBody);
        return NoticeDto.from(notices.save(notice));
    }

    @PutMapping("/{id}/")
    @Transactional
    public NoticeDto replace(@PathVariable Long id, @RequestBody Map<String, Object> requestBody) {
        return update(id, requestBody);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        notices.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(Notice notice, Map<String, Object> requestBody) {
        if (requestBody.containsKey("is_pinned")) {
            notice.setPinned(Body.asBool(requestBody, "is_pinned"));
        }
        if (requestBody.containsKey("expiry_date")) {
            notice.setExpiryDate(Body.asDateTime(requestBody, "expiry_date"));
        }
    }

    private Notice scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return notices.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
