package com.nibash.device;

import com.nibash.access.AccessCard;
import com.nibash.access.AccessCardRepository;
import com.nibash.building.BuildingRepository;
import com.nibash.common.Times;
import com.nibash.gate.GateEvent;
import com.nibash.gate.GateEventRepository;
import com.nibash.intercom.IntercomDevice;
import com.nibash.intercom.IntercomDeviceRepository;
import com.nibash.intercom.IntercomLog;
import com.nibash.intercom.IntercomLogRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the device gateway's connection threads do to the database. Each method is its own short
 * transaction, run on the calling connection's thread, so a panel's events land in the same tables
 * the web app reads — {@code intercom_logs} for rings and card swipes, {@code gate_events} for the
 * door — and show up on the Safety and Gate log pages.
 */
@Service
public class DeviceService {

    /** A panel that has said HELLO and been accepted. */
    public record Session(Long deviceId, Long buildingId, String deviceName) {
    }

    /** Refusal of a HELLO; the message goes back to the panel after {@code ERR}. */
    public static class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    private final IntercomDeviceRepository devices;
    private final IntercomLogRepository logs;
    private final GateEventRepository gateEvents;
    private final AccessCardRepository cards;
    private final BuildingRepository buildings;
    private final String secret;

    public DeviceService(IntercomDeviceRepository devices, IntercomLogRepository logs,
                         GateEventRepository gateEvents, AccessCardRepository cards, BuildingRepository buildings,
                         @Value("${nibash.devices.secret:}") String secret) {
        this.devices = devices;
        this.logs = logs;
        this.gateEvents = gateEvents;
        this.cards = cards;
        this.buildings = buildings;
        this.secret = secret == null ? "" : secret.trim();
    }

    @Transactional(readOnly = true)
    public Session authenticate(long deviceId, String givenSecret, InetAddress remote) throws Refused {
        IntercomDevice device = devices.findById(deviceId).orElseThrow(() -> new Refused("unknown device"));
        if (!trusted(secret, givenSecret, remote, device.getIpAddress())) {
            throw new Refused(secret.isEmpty() ? "not the address registered for this device" : "wrong secret");
        }
        return new Session(device.getId(), device.getBuilding().getId(), device.getDeviceName());
    }

    /**
     * With a shared secret configured, a panel must present it (compared in constant time). Without
     * one, the gateway trusts only the address the panel was registered with — or this machine, for
     * the bundled simulator.
     */
    static boolean trusted(String configuredSecret, String givenSecret, InetAddress remote, String registeredIp) {
        if (!configuredSecret.isEmpty()) {
            return givenSecret != null && MessageDigest.isEqual(configuredSecret.getBytes(StandardCharsets.UTF_8),
                    givenSecret.getBytes(StandardCharsets.UTF_8));
        }
        if (remote.isLoopbackAddress()) {
            return true;
        }
        try {
            return InetAddress.getByName(registeredIp).equals(remote); // an IP literal: no DNS lookup
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Transactional
    public void log(Long deviceId, String eventType, String details) {
        IntercomLog log = new IntercomLog();
        log.setDevice(devices.getReferenceById(deviceId));
        log.setEventType(eventType);
        log.setTimestamp(Times.now());
        log.setDetails(details);
        logs.save(log);
    }

    /**
     * A card swiped at the panel. It opens the door only if it is active and belongs to a resident of
     * this panel's building; either way the swipe is logged.
     *
     * @return the line to send back: {@code ALLOW <first name>} or {@code DENY <reason>}
     */
    @Transactional
    public String checkCard(Session session, String cardNumber) {
        AccessCard card = cards.findByCardNumber(cardNumber).orElse(null);
        String refusal = card == null ? "unknown card"
                : !card.getResident().getBuilding().getId().equals(session.buildingId()) ? "not valid for this building"
                : !"active".equals(card.getStatus()) ? "card " + card.getStatus()
                : null;
        if (refusal != null) {
            log(session.deviceId(), "card_denied", "Card " + cardNumber + " refused: " + refusal + ".");
            return "DENY " + refusal;
        }
        String holder = card.getResident().getUser().getName();
        log(session.deviceId(), "card_allowed", "Card " + cardNumber + " — " + holder + ".");
        return "ALLOW " + holder.split("\\s+")[0];
    }

    /** The panel's door sensor: an open or close event in the gate log, with no person attached. */
    @Transactional
    public void door(Session session, boolean opened) {
        GateEvent event = new GateEvent();
        event.setBuilding(buildings.getReferenceById(session.buildingId()));
        event.setEventType(opened ? "open" : "close");
        event.setTimestamp(Times.now());
        gateEvents.save(event);
    }
}
