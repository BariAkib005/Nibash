package com.nibash.intercom;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.common.Times;
import com.nibash.device.DeviceGateway;
import com.nibash.device.DeviceRegistry;
import com.nibash.tenancy.TenantService;
import com.nibash.user.Roles;
import com.nibash.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Intercom (spec §8.12): {@code /api/intercom/devices/} (CommitteeOrAdmin),
 * {@code /api/intercom/logs/} (IsAuthenticated) and the hardware's {@code POST /api/intercom/webhook}.
 *
 * <p>The webhook is {@code AllowAny} in the spec, which lets anyone forge door events. When
 * {@code nibash.intercom.webhook-secret} is set, the panel must send it as {@code X-Intercom-Secret};
 * left blank (the default) the endpoint behaves exactly as specified.
 *
 * <p>Panels can also stay connected over TCP to the {@link DeviceGateway}; the device list then
 * reports which are online, and {@code POST /api/intercom/devices/{id}/open/} pushes {@code OPEN}
 * down that connection to release the door.
 */
@RestController
@RequestMapping("/api/intercom")
public class IntercomController {

    /** Loose IPv4/IPv6 shape check — enough to stop a typo, not a full RFC parser. */
    private static final Pattern IP = Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:]{2,45})$");

    private final IntercomDeviceRepository devices;
    private final IntercomLogRepository logs;
    private final BuildingRepository buildings;
    private final TenantService tenancy;
    private final DeviceRegistry registry;
    private final DeviceGateway gateway;
    private final String webhookSecret;

    /** How long an {@code OPEN} from the app holds the door released. */
    static final int RELEASE_SECONDS = 5;

    public IntercomController(IntercomDeviceRepository devices, IntercomLogRepository logs,
                              BuildingRepository buildings, TenantService tenancy, DeviceRegistry registry,
                              DeviceGateway gateway,
                              @Value("${nibash.intercom.webhook-secret:}") String webhookSecret) {
        this.devices = devices;
        this.logs = logs;
        this.buildings = buildings;
        this.tenancy = tenancy;
        this.registry = registry;
        this.gateway = gateway;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    /** {@code online}, {@code connected_since} and {@code last_seen} come from the live gateway connection. */
    public record DeviceDto(Long id, Long building, String deviceName, String ipAddress, boolean online,
                            LocalDateTime connectedSince, LocalDateTime lastSeen) {

        public static DeviceDto from(IntercomDevice d, DeviceRegistry registry) {
            var status = registry.status(d.getId());
            return new DeviceDto(d.getId(), d.getBuilding().getId(), d.getDeviceName(), d.getIpAddress(),
                    status.isPresent(), status.map(DeviceRegistry.Status::connectedAt).orElse(null),
                    status.map(DeviceRegistry.Status::lastSeen).orElse(null));
        }
    }

    public record LogDto(Long id, Long device, String eventType, LocalDateTime timestamp, String details,
                         String deviceName) {

        public static LogDto from(IntercomLog l) {
            return new LogDto(l.getId(), l.getDevice().getId(), l.getEventType(), l.getTimestamp(), l.getDetails(),
                    l.getDevice().getDeviceName());
        }
    }

    // ---------------------------------------------------------------- webhook (AllowAny)

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(name = "X-Intercom-Secret", required = false) String secret,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!webhookSecret.isEmpty() && (secret == null || !MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8)))) {
            throw ApiException.forbidden("Invalid webhook secret.");
        }
        Map<String, Object> input = body == null ? Map.of() : body;
        IntercomDevice device = devices.findById(Body.requireLong(input, "device_id"))
                .orElseThrow(() -> ApiException.notFound("Not found."));

        IntercomLog log = new IntercomLog();
        log.setDevice(device);
        log.setEventType(requireEventType(input));
        LocalDateTime timestamp = Body.asDateTime(input, "timestamp");
        log.setTimestamp(timestamp == null ? Times.now() : Times.toStorage(timestamp));
        log.setDetails(Body.str(input, "details"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", logs.save(log).getId()));
    }

    // ---------------------------------------------------------------- devices

    @GetMapping("/devices/")
    @Transactional(readOnly = true)
    public PageEnvelope<DeviceDto> listDevices(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("deviceName"));
        return PageEnvelope.of(devices.findByBuildingIdIn(scope, pageable), d -> DeviceDto.from(d, registry));
    }

    @GetMapping("/devices/{id}/")
    @Transactional(readOnly = true)
    public DeviceDto device(@PathVariable Long id) {
        return DeviceDto.from(scopedDevice(id), registry);
    }

    @PostMapping("/devices/")
    @Transactional
    public ResponseEntity<DeviceDto> createDevice(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        IntercomDevice device = new IntercomDevice();
        device.setBuilding(buildings.findById(buildingId).orElseThrow(() -> ApiException.notFound("Not found.")));
        device.setDeviceName(Body.requireStr(body, "device_name"));
        device.setIpAddress(requireIp(body, buildingId, null));
        return ResponseEntity.status(HttpStatus.CREATED).body(DeviceDto.from(devices.save(device), registry));
    }

    @PatchMapping("/devices/{id}/")
    @Transactional
    public DeviceDto updateDevice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        IntercomDevice device = scopedDevice(id);
        if (body.containsKey("device_name")) {
            device.setDeviceName(Body.requireStr(body, "device_name"));
        }
        if (body.containsKey("ip_address")) {
            device.setIpAddress(requireIp(body, device.getBuilding().getId(), device.getId()));
        }
        return DeviceDto.from(devices.save(device), registry);
    }

    /**
     * Releases the door from the app: {@code OPEN <seconds> <name>} is written to the panel's live
     * connection by this request's thread. Guards and managers only; an offline panel is a 400.
     */
    @PostMapping("/devices/{id}/open/")
    @Transactional
    public Map<String, Object> open(@PathVariable Long id) {
        User caller = CurrentUser.require();
        if (!caller.isBackOffice() && !caller.isAdminOrCommittee() && !Roles.GUARD.equals(caller.getRole())) {
            throw ApiException.forbidden("Only guards and the committee can open the door.");
        }
        IntercomDevice device = scopedDevice(id);
        if (!registry.send(device.getId(), "OPEN " + RELEASE_SECONDS + " " + caller.getName())) {
            throw ApiException.badRequest(device.getDeviceName() + " is offline, so the door can't be opened from here.");
        }
        IntercomLog log = new IntercomLog();
        log.setDevice(device);
        log.setEventType("remote_open");
        log.setTimestamp(Times.now());
        log.setDetails("Door released from Nibash by " + caller.getName() + ".");
        logs.save(log);
        return Map.of("detail", "Door released at " + device.getDeviceName() + ".");
    }

    /** Where panels connect, and how many are connected across the caller's view. */
    @GetMapping("/gateway/")
    public Map<String, Object> gateway() {
        CurrentUser.require();
        return Map.of("enabled", gateway.isEnabled(), "port", gateway.port(), "connected", registry.size());
    }

    @PutMapping("/devices/{id}/")
    @Transactional
    public DeviceDto replaceDevice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateDevice(id, body);
    }

    @DeleteMapping("/devices/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        Policy.requireManager();
        IntercomDevice device = scopedDevice(id);
        if (logs.existsByDeviceId(device.getId())) {
            throw ApiException.badRequest("This device has logged events, so it can't be deleted.");
        }
        devices.delete(device);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- logs

    @GetMapping("/logs/")
    @Transactional(readOnly = true)
    public PageEnvelope<LogDto> listLogs(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(name = "device_id", required = false) Long deviceId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id")));
        var rows = deviceId == null
                ? logs.findByDeviceBuildingIdIn(scope, pageable)
                : logs.findByDeviceBuildingIdInAndDeviceId(scope, deviceId, pageable);
        return PageEnvelope.of(rows, LogDto::from);
    }

    @GetMapping("/logs/{id}/")
    @Transactional(readOnly = true)
    public LogDto log(@PathVariable Long id) {
        return LogDto.from(scopedLog(id));
    }

    @PostMapping("/logs/")
    @Transactional
    public ResponseEntity<LogDto> createLog(@RequestBody Map<String, Object> body) {
        IntercomLog log = new IntercomLog();
        log.setDevice(scopedDevice(Body.requireLong(body, "device")));
        log.setEventType(requireEventType(body));
        LocalDateTime timestamp = Body.asDateTime(body, "timestamp");
        log.setTimestamp(timestamp == null ? Times.now() : Times.toStorage(timestamp));
        log.setDetails(Body.str(body, "details"));
        return ResponseEntity.status(HttpStatus.CREATED).body(LogDto.from(logs.save(log)));
    }

    @DeleteMapping("/logs/{id}/")
    @Transactional
    public ResponseEntity<Void> deleteLog(@PathVariable Long id) {
        Policy.requireManager();
        logs.delete(scopedLog(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ---------------------------------------------------------------- helpers

    private String requireEventType(Map<String, Object> body) {
        String type = Body.requireStr(body, "event_type");
        if (type.length() > 50) {
            throw ApiException.badRequest("event_type must be 50 characters or fewer");
        }
        return type;
    }

    private String requireIp(Map<String, Object> body, Long buildingId, Long selfId) {
        String ip = Body.requireStr(body, "ip_address");
        if (!IP.matcher(ip).matches()) {
            throw ApiException.badRequest("ip_address must be an IPv4 or IPv6 address");
        }
        devices.findByBuildingIdAndIpAddress(buildingId, ip)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw ApiException.badRequest("Another device in this building already uses " + ip + ".");
                });
        return ip;
    }

    private IntercomDevice scopedDevice(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return devices.findByIdAndBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }

    private IntercomLog scopedLog(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return logs.findByIdAndDeviceBuildingIdIn(id, allowed).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
