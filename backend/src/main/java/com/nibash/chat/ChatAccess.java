package com.nibash.chat;

import com.nibash.common.ApiException;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may read which room — the one rule every chat path (REST and WebSocket) shares.
 *
 * <p>Tenant scoping comes first, as everywhere (spec §6). On top of it, a room with
 * {@code is_public = false} is readable only by its members and by managers (admin, committee,
 * back-office). The spec leaves private rooms unenforced; without this a "private" committee room
 * would be private in name only, so it is a deliberate tightening, noted in the README.
 */
@Service
public class ChatAccess {

    private final ChatRoomRepository rooms;
    private final RoomMemberRepository members;
    private final TenantService tenancy;

    public ChatAccess(ChatRoomRepository rooms, RoomMemberRepository members, TenantService tenancy) {
        this.rooms = rooms;
        this.members = members;
        this.tenancy = tenancy;
    }

    /** Readable rooms inside {@code scope}, ordered by latest activity. */
    @Transactional(readOnly = true)
    public List<ChatRoom> readableRooms(User caller, List<Long> scope) {
        if (scope.isEmpty()) {
            return List.of();
        }
        Set<Long> joined = new HashSet<>(members.findRoomIdsForUser(caller.getId()));
        boolean manager = isManager(caller);
        return rooms.findByActivity(scope).stream()
                .filter(room -> room.isPublicRoom() || manager || joined.contains(room.getId()))
                .toList();
    }

    public List<Long> readableRoomIds(User caller, List<Long> scope) {
        return readableRooms(caller, scope).stream().map(ChatRoom::getId).toList();
    }

    /** The room if the caller may read it; a foreign or private-to-others room is a plain 404. */
    @Transactional(readOnly = true)
    public ChatRoom requireReadable(User caller, Long roomId) {
        List<Long> allowed = tenancy.allowedBuildingIds(caller);
        ChatRoom room = rooms.findByIdAndBuildingIdIn(roomId, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
        if (!room.isPublicRoom() && !isManager(caller)
                && !members.existsByRoomIdAndResidentUserId(room.getId(), caller.getId())) {
            throw ApiException.notFound("Not found.");
        }
        return room;
    }

    public static boolean isManager(User caller) {
        return caller.isBackOffice() || caller.isAdminOrCommittee();
    }
}
