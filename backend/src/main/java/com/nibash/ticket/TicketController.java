package com.nibash.ticket;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.Staff;
import com.nibash.staffing.StaffRepository;
import com.nibash.storage.StorageService;
import com.nibash.tenancy.TenantService;
import com.nibash.ticket.TicketDtos.TicketDto;
import com.nibash.ticket.TicketDtos.TicketImageDto;
import com.nibash.user.User;
import com.nibash.vendor.VendorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** {@code /api/tickets/} — IsAuthenticated (spec §8.7). */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    /** Spec §8.7 caps ticket photos at 5 MB, separately from the servlet-wide 10 MB limit. */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final TicketRepository tickets;
    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final VendorRepository vendors;
    private final TenantService tenancy;
    private final StorageService storage;

    public TicketController(TicketRepository tickets, ResidentRepository residents, StaffRepository staff,
                            VendorRepository vendors, TenantService tenancy, StorageService storage) {
        this.tickets = tickets;
        this.residents = residents;
        this.staff = staff;
        this.vendors = vendors;
        this.tenancy = tenancy;
        this.storage = storage;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<TicketDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId,
                                        @RequestParam(name = "resident_id", required = false) Long residentId,
                                        @RequestParam(required = false) String status) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        String filter = status == null || status.isBlank() ? null : status;
        Body.requireOneOf(filter, Ticket.STATUSES, "status");
        return PageEnvelope.of(tickets.search(scope, filter, residentId, pageable), TicketDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public TicketDto detail(@PathVariable Long id) {
        return TicketDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<TicketDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();

        Long residentId = Body.requireLong(body, "resident");
        Resident resident = residents.findById(residentId)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        tenancy.requireAccess(caller, resident.getBuilding().getId());

        Ticket ticket = new Ticket();
        ticket.setResident(resident);
        ticket.setBuilding(resident.getBuilding());
        ticket.setCategory(Body.requireStr(body, "category"));
        ticket.setDescription(Body.requireStr(body, "description"));
        apply(ticket, body);

        if (ticket.getAssignedTo() == null) {
            autoAssign(ticket).ifPresent(ticket::setAssignedTo);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketDto.from(tickets.save(ticket)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public TicketDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Ticket ticket = scoped(id);
        if (body.containsKey("category")) {
            ticket.setCategory(Body.requireStr(body, "category"));
        }
        if (body.containsKey("description")) {
            ticket.setDescription(Body.requireStr(body, "description"));
        }
        apply(ticket, body);
        return TicketDto.from(tickets.save(ticket));
    }

    @PutMapping("/{id}/")
    @Transactional
    public TicketDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tickets.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Move a ticket across the board (spec §8.7). Reaching a terminal column stamps {@code
     * closed_at}; moving back out of one clears it, so the field never claims a reopened ticket is
     * still closed.
     */
    @PatchMapping("/{id}/status/")
    @Transactional
    public TicketDto changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Ticket ticket = scoped(id);
        String status = Body.requireStr(body, "status");
        Body.requireOneOf(status, Ticket.STATUSES, "status");

        ticket.setStatus(status);
        boolean terminal = Ticket.RESOLVED.equals(status) || Ticket.CLOSED.equals(status);
        ticket.setClosedAt(terminal ? LocalDateTime.now() : null);
        return TicketDto.from(tickets.save(ticket));
    }

    /** Attach a photo (spec §8.7). */
    @PostMapping("/{id}/images/")
    @Transactional
    public ResponseEntity<TicketImageDto> addImage(@PathVariable Long id,
                                                   @RequestPart(name = "file", required = false) MultipartFile file) {
        Ticket ticket = scoped(id);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("file is required");
        }
        storage.requireAtMost(file, MAX_IMAGE_BYTES, "file must be 5MB or smaller");

        TicketImage image = new TicketImage();
        image.setTicket(ticket);
        image.setImagePath(storage.store(file, "tickets/" + ticket.getId()));
        ticket.getImages().add(image);

        Ticket saved = tickets.save(ticket);
        TicketImage stored = saved.getImages().getLast();
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketImageDto.from(stored));
    }

    /**
     * Auto-assignment (spec §8.7): prefer a staff member whose role names the ticket's category,
     * fall back to anyone on the building's staff, and leave it unassigned if there is nobody.
     */
    private java.util.Optional<Staff> autoAssign(Ticket ticket) {
        Long buildingId = ticket.getBuilding().getId();
        List<Staff> matching = staff.findByBuildingAndRoleMatching(buildingId, ticket.getCategory());
        if (!matching.isEmpty()) {
            return java.util.Optional.of(matching.getFirst());
        }
        return staff.findByBuildingIdOrderByNameAsc(buildingId).stream().findFirst();
    }

    private void apply(Ticket ticket, Map<String, Object> body) {
        if (body.containsKey("status")) {
            String status = Body.str(body, "status");
            Body.requireOneOf(status, Ticket.STATUSES, "status");
            ticket.setStatus(status);
        }
        if (body.containsKey("priority")) {
            String priority = Body.str(body, "priority");
            Body.requireOneOf(priority, Ticket.PRIORITIES, "priority");
            ticket.setPriority(priority);
        }
        if (body.containsKey("assigned_to")) {
            Long staffId = Body.asLong(body, "assigned_to");
            ticket.setAssignedTo(staffId == null ? null : scopedStaff(staffId));
        }
        if (body.containsKey("service_vendor")) {
            Long vendorId = Body.asLong(body, "service_vendor");
            ticket.setServiceVendor(vendorId == null ? null
                    : vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Not found.")));
        }
    }

    private Staff scopedStaff(Long staffId) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return staff.findByIdAndBuildingIdIn(staffId, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private Ticket scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return tickets.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
