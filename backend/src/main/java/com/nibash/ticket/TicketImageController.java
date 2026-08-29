package com.nibash.ticket;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.PageEnvelope;
import com.nibash.tenancy.TenantService;
import com.nibash.ticket.TicketDtos.TicketImageDto;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/ticket-images/} — IsAuthenticated (spec §8.7). Uploads go through
 * {@code POST /api/tickets/{id}/images/}; this resource only lists and removes them.
 */
@RestController
@RequestMapping("/api/ticket-images")
public class TicketImageController {

    private final TicketImageRepository images;
    private final TenantService tenancy;

    public TicketImageController(TicketImageRepository images, TenantService tenancy) {
        this.images = images;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<TicketImageDto> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        return PageEnvelope.of(images.findByTicketBuildingIdIn(scope, pageable), TicketImageDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public TicketImageDto detail(@PathVariable Long id) {
        return TicketImageDto.from(scoped(id));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        images.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private TicketImage scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return images.findByIdAndTicketBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
