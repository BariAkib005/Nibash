package com.nibash.device;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Which panels are connected right now, shared between the gateway's connection threads (which
 * add and remove themselves) and web request threads (which read status and push commands).
 *
 * <p>A {@link ConcurrentHashMap} keyed by device id. Removal is conditional on the value, so a
 * connection that was replaced by a newer one from the same panel cannot unregister its successor
 * as it winds down.
 */
@Component
public class DeviceRegistry {

    /** What the web app shows about a connected panel. */
    public record Status(LocalDateTime connectedAt, LocalDateTime lastSeen, String address) {
    }

    private final ConcurrentMap<Long, DeviceConnection> online = new ConcurrentHashMap<>();

    /** @return the connection this one replaces, if the panel was already connected */
    DeviceConnection register(Long deviceId, DeviceConnection connection) {
        return online.put(deviceId, connection);
    }

    /** @return true if {@code connection} was the registered one and has now been removed */
    boolean remove(Long deviceId, DeviceConnection connection) {
        return online.remove(deviceId, connection);
    }

    public Optional<Status> status(Long deviceId) {
        DeviceConnection connection = online.get(deviceId);
        return connection == null
                ? Optional.empty()
                : Optional.of(new Status(connection.connectedAt(), connection.lastSeen(), connection.address()));
    }

    /**
     * Pushes one line to a connected panel.
     *
     * @return false if the panel isn't connected or the line couldn't be written
     */
    public boolean send(Long deviceId, String line) {
        DeviceConnection connection = online.get(deviceId);
        return connection != null && connection.send(line);
    }

    public int size() {
        return online.size();
    }

    void closeAll(String reason) {
        List.copyOf(online.values()).forEach(connection -> connection.close(reason));
    }
}
