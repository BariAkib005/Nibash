package com.nibash.chat;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
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

/**
 * {@code /api/chat/members/} — IsAuthenticated (spec §8.13).
 *
 * <p>A resident may join a public room themselves and may leave any room; adding someone else, or
 * adding anyone to a private room, is a manager's action.
 */
@RestController
@RequestMapping("/api/chat/members")
public class RoomMemberController {

    private final RoomMemberRepository members;
    private final ResidentRepository residents;
    private final ChatAccess access;
    private final TenantService tenancy;

    public RoomMemberController(RoomMemberRepository members, ResidentRepository residents,
                                ChatAccess access, TenantService tenancy) {
        this.members = members;
        this.residents = residents;
        this.access = access;
        this.tenancy = tenancy;
    }

    public record MemberDto(Long id, Long room, Long resident, LocalDateTime joinedAt,
                            String residentName, String unitNumber) {

        public static MemberDto from(RoomMember m) {
            Resident r = m.getResident();
            return new MemberDto(m.getId(), m.getRoom().getId(), r.getId(), m.getJoinedAt(),
                    r.getUser().getName(), r.getUnit() == null ? null : r.getUnit().getUnitNumber());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<MemberDto> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "building_id", required = false) Long buildingId,
                                        @RequestParam(name = "room_id", required = false) Long roomId) {
        User caller = CurrentUser.require();
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("joinedAt"));
        if (roomId != null) {
            access.requireReadable(caller, roomId);
            List<Long> allowed = tenancy.allowedBuildingIds(caller);
            return PageEnvelope.of(members.findByRoomBuildingIdInAndRoomId(allowed, roomId, pageable), MemberDto::from);
        }
        List<Long> scope = tenancy.resolveScope(caller, buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        return PageEnvelope.of(members.findByRoomBuildingIdIn(scope, pageable), MemberDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public MemberDto detail(@PathVariable Long id) {
        return MemberDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<MemberDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        ChatRoom room = access.requireReadable(caller, Body.requireLong(body, "room"));
        boolean manager = ChatAccess.isManager(caller);

        Long residentId = Body.asLong(body, "resident");
        Resident resident = residentId == null
                ? residents.findByUserIdAndBuildingId(caller.getId(), room.getBuilding().getId())
                        .orElseThrow(() -> ApiException.badRequest("Only residents can join a room."))
                : residents.findById(residentId).orElseThrow(() -> ApiException.notFound("Not found."));

        boolean self = resident.getUser().getId().equals(caller.getId());
        if (!manager && (!self || !room.isPublicRoom())) {
            throw ApiException.forbidden("You can only join public rooms yourself.");
        }
        if (!resident.getBuilding().getId().equals(room.getBuilding().getId())) {
            throw ApiException.badRequest("That resident does not live in this room's building.");
        }

        // Joining twice is a no-op rather than a unique-key 500.
        RoomMember member = members.findByRoomIdAndResidentId(room.getId(), resident.getId()).orElseGet(() -> {
            RoomMember created = new RoomMember();
            created.setRoom(room);
            created.setResident(resident);
            return members.save(created);
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberDto.from(member));
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User caller = CurrentUser.require();
        RoomMember member = scoped(id);
        if (!ChatAccess.isManager(caller) && !member.getResident().getUser().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You can only leave rooms yourself.");
        }
        members.delete(member);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private RoomMember scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return members.findByIdAndRoomBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
