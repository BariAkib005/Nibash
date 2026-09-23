package com.nibash.chat;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.notification.Notification;
import com.nibash.notification.NotificationRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/chat/messages/} — IsAuthenticated (spec §8.13). Persistence lives here; the
 * WebSocket only delivers.
 *
 * <ul>
 *   <li>Filters: {@code ?room_id=}, {@code ?search=} (content, case-insensitive),
 *       {@code ?latest=true} (newest first; default oldest first).</li>
 *   <li><b>Side effect on create:</b> one {@code type='chat'} notification per <i>other</i> member.</li>
 *   <li>The sender is always the caller's own resident row — a {@code resident} in the body is
 *       ignored, so nobody can post as a neighbour.</li>
 *   <li>After the transaction commits, the saved message is pushed to every socket in the room,
 *       so live delivery shows exactly what was stored and never a message that rolled back.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat/messages")
public class ChatMessageController {

    private final MessageRepository messages;
    private final RoomMemberRepository members;
    private final ResidentRepository residents;
    private final NotificationRepository notifications;
    private final ChatAccess access;
    private final ChatSocketRegistry sockets;
    private final TenantService tenancy;

    public ChatMessageController(MessageRepository messages, RoomMemberRepository members,
                                 ResidentRepository residents, NotificationRepository notifications,
                                 ChatAccess access, ChatSocketRegistry sockets, TenantService tenancy) {
        this.messages = messages;
        this.members = members;
        this.residents = residents;
        this.notifications = notifications;
        this.access = access;
        this.sockets = sockets;
        this.tenancy = tenancy;
    }

    public record MessageDto(Long id, Long room, Long resident, String content, LocalDateTime sentAt,
                             String senderName, Long senderUser) {

        public static MessageDto from(Message m) {
            return new MessageDto(m.getId(), m.getRoom().getId(), m.getResident().getId(), m.getContent(),
                    m.getSentAt(), m.getResident().getUser().getName(), m.getResident().getUser().getId());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<MessageDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "room_id", required = false) Long roomId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "false") boolean latest) {
        User caller = CurrentUser.require();
        List<Long> readable = readableRoomIds(caller, buildingId, roomId);
        if (readable.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        Sort.Direction direction = latest ? Sort.Direction.DESC : Sort.Direction.ASC;
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(direction, "sentAt").and(Sort.by(direction, "id")));
        String needle = search == null || search.isBlank() ? null : search.trim();
        return PageEnvelope.of(messages.search(readable, roomId, needle, pageable), MessageDto::from);
    }

    /**
     * {@code GET /api/chat/messages/summary/?room_id=} → {@code {messages, rooms, latest}}.
     * {@code rooms} counts only the caller's readable rooms — the spec §15.3 fix for the original,
     * which counted every room in the system.
     */
    @GetMapping("/summary/")
    @Transactional(readOnly = true)
    public Map<String, Object> summary(@RequestParam(name = "building_id", required = false) Long buildingId,
                                       @RequestParam(name = "room_id", required = false) Long roomId) {
        User caller = CurrentUser.require();
        List<Long> rooms = access.readableRoomIds(caller, tenancy.resolveScope(caller, buildingId));
        List<Long> counted = roomId == null ? rooms : readableRoomIds(caller, buildingId, roomId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", counted.isEmpty() ? 0 : messages.countByRoomIdIn(counted));
        out.put("rooms", rooms.size());
        out.put("latest", counted.isEmpty() ? null
                : messages.findFirstByRoomIdInOrderBySentAtDescIdDesc(counted).map(MessageDto::from).orElse(null));
        return out;
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public MessageDto detail(@PathVariable Long id) {
        return MessageDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<MessageDto> create(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        ChatRoom room = access.requireReadable(caller, Body.requireLong(body, "room"));
        String content = Body.requireStr(body, "content");
        if (content.length() > 4000) {
            throw ApiException.badRequest("Messages are limited to 4000 characters.");
        }

        Resident sender = residents.findByUserIdAndBuildingId(caller.getId(), room.getBuilding().getId())
                .orElseThrow(() -> ApiException.badRequest("Only residents can post messages."));

        // Posting in a public room makes you a member, so replies notify you.
        if (members.findByRoomIdAndResidentId(room.getId(), sender.getId()).isEmpty()) {
            if (!room.isPublicRoom()) {
                throw ApiException.forbidden("You are not a member of this room.");
            }
            RoomMember joined = new RoomMember();
            joined.setRoom(room);
            joined.setResident(sender);
            members.save(joined);
        }

        Message message = new Message();
        message.setRoom(room);
        message.setResident(sender);
        message.setContent(content);
        Message saved = messages.save(message);

        List<Notification> fanOut = members.findOthers(room.getId(), sender.getId()).stream().map(member -> {
            Notification n = new Notification();
            n.setBuilding(room.getBuilding());
            n.setResident(member.getResident());
            n.setType("chat");
            n.setMessage("New message in " + room.getName());
            n.setSentAt(Times.now());
            return n;
        }).toList();
        notifications.saveAll(fanOut);

        MessageDto dto = MessageDto.from(saved);
        afterCommit(() -> sockets.broadcastCreated(room.getId(), dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}/")
    @Transactional
    public MessageDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Message message = ownMessage(id);
        if (body.containsKey("content")) {
            message.setContent(Body.requireStr(body, "content"));
        }
        return MessageDto.from(messages.save(message));
    }

    @PutMapping("/{id}/")
    @Transactional
    public MessageDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        messages.delete(ownMessage(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private List<Long> readableRoomIds(User caller, Long buildingId, Long roomId) {
        if (roomId != null) {
            return List.of(access.requireReadable(caller, roomId).getId());
        }
        return access.readableRoomIds(caller, tenancy.resolveScope(caller, buildingId));
    }

    private Message ownMessage(Long id) {
        User caller = CurrentUser.require();
        Message message = scoped(id);
        if (!ChatAccess.isManager(caller) && !message.getResident().getUser().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You can only change your own messages.");
        }
        return message;
    }

    private Message scoped(Long id) {
        User caller = CurrentUser.require();
        List<Long> readable = access.readableRoomIds(caller, tenancy.allowedBuildingIds(caller));
        return messages.findByIdAndRoomIdIn(id, readable.isEmpty() ? List.of(-1L) : readable)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
