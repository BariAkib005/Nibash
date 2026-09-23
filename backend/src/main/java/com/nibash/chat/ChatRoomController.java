package com.nibash.chat;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/chat/rooms/} — IsAuthenticated (spec §8.13). Listed by latest activity.
 *
 * <p>Any member of the building may open a room (and joins it automatically); renaming or
 * deleting a room is left to managers, so a resident cannot delete the building's General room.
 */
@RestController
@RequestMapping("/api/chat/rooms")
public class ChatRoomController {

    private final ChatRoomRepository rooms;
    private final RoomMemberRepository members;
    private final ResidentRepository residents;
    private final BuildingRepository buildings;
    private final ChatAccess access;
    private final TenantService tenancy;

    public ChatRoomController(ChatRoomRepository rooms, RoomMemberRepository members, ResidentRepository residents,
                              BuildingRepository buildings, ChatAccess access, TenantService tenancy) {
        this.rooms = rooms;
        this.members = members;
        this.residents = residents;
        this.buildings = buildings;
        this.access = access;
        this.tenancy = tenancy;
    }

    public record RoomDto(Long id, String name, boolean isPublic, Long building) {

        public static RoomDto from(ChatRoom r) {
            return new RoomDto(r.getId(), r.getName(), r.isPublicRoom(), r.getBuilding().getId());
        }
    }

    /** Not paged in practice (a building has a handful of rooms) but keeps the envelope shape. */
    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<RoomDto> list(@RequestParam(name = "building_id", required = false) Long buildingId) {
        User caller = CurrentUser.require();
        List<RoomDto> results = access.readableRooms(caller, tenancy.resolveScope(caller, buildingId))
                .stream().map(RoomDto::from).toList();
        return new PageEnvelope<>(results.size(), null, null, results);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public RoomDto detail(@PathVariable Long id) {
        return RoomDto.from(access.requireReadable(CurrentUser.require(), id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<RoomDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        ChatRoom room = new ChatRoom();
        room.setBuilding(buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found.")));
        room.setName(Body.requireStr(body, "name"));
        if (body.containsKey("is_public")) {
            room.setPublicRoom(Body.asBool(body, "is_public"));
        }
        ChatRoom saved = rooms.save(room);

        // The creator is in the room they opened — otherwise a private room would lock them out.
        residents.findByUserIdAndBuildingId(caller.getId(), buildingId).ifPresent(resident -> {
            RoomMember member = new RoomMember();
            member.setRoom(saved);
            member.setResident(resident);
            members.save(member);
        });
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomDto.from(saved));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public RoomDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        ChatRoom room = access.requireReadable(CurrentUser.require(), id);
        if (body.containsKey("name")) {
            room.setName(Body.requireStr(body, "name"));
        }
        if (body.containsKey("is_public")) {
            room.setPublicRoom(Body.asBool(body, "is_public"));
        }
        return RoomDto.from(rooms.save(room));
    }

    @PutMapping("/{id}/")
    @Transactional
    public RoomDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    /** Members and messages go with the room ({@code ON DELETE CASCADE} in the schema). */
    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        rooms.delete(access.requireReadable(CurrentUser.require(), id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
