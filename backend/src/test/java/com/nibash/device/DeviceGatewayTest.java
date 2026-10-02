package com.nibash.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.nibash.intercom.IntercomDeviceRepository;
import com.nibash.support.ApiTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;

/**
 * The door-hardware gateway over real TCP sockets: the server is the application's own
 * {@link DeviceGateway} on a random port, and each "panel" here is a plain {@link Socket} speaking
 * the line protocol, exactly as a panel's firmware or the bundled simulator would.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceGatewayTest extends ApiTestSupport {

    private static final String ADMIN = "admin1@nibash.bd";
    private static final String GUARD = "guard1@nibash.bd";
    private static final String RESIDENT = "resident1@nibash.bd";

    @Autowired DeviceGateway gateway;
    @Autowired DeviceRegistry registry;
    @Autowired IntercomDeviceRepository devices;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void aPanelSignsInChecksCardsAndReportsTheDoor() throws Exception {
        String guard = tokenFor(GUARD);
        long building = buildingId(guard);
        long device = northGate(guard);

        try (Panel stranger = new Panel(gateway.port())) {
            assertThat(stranger.say("PING")).startsWith("ERR say HELLO");
            assertThat(stranger.readOrNull()).isNull(); // and the line is closed
        }
        try (Panel unknown = new Panel(gateway.port())) {
            assertThat(unknown.say("HELLO 999999")).isEqualTo("ERR unknown device");
        }

        long gateBefore = getJson("/api/gate-events/?building_id=" + building, guard).get("count").asLong();
        try (Panel panel = new Panel(gateway.port())) {
            assertThat(panel.say("HELLO " + device)).isEqualTo("OK North Gate Intercom");
            assertThat(panel.say("PING")).isEqualTo("PONG");
            assertThat(panel.say("CARD GLH-AC-0001")).isEqualTo("ALLOW Ayesha");
            assertThat(panel.say("CARD NOT-A-CARD")).isEqualTo("DENY unknown card");
            assertThat(panel.say("RING 01A")).isEqualTo("OK");
            assertThat(panel.say("DOOR OPENED")).isEqualTo("OK");
            assertThat(panel.say("DOOR CLOSED")).isEqualTo("OK");
            assertThat(panel.say("DOOR AJAR")).startsWith("ERR usage");
            assertThat(panel.say("FLY")).isEqualTo("ERR unknown command");

            JsonNode listed = deviceRow(guard, building, device);
            assertThat(listed.get("online").asBoolean()).isTrue();
            assertThat(listed.get("connected_since").isNull()).isFalse();
            assertThat(panel.say("BYE")).isEqualTo("BYE");
        }

        // The door sensor landed in the gate log; the swipes, ring and connection in the intercom log.
        assertThat(getJson("/api/gate-events/?building_id=" + building, guard).get("count").asLong())
                .isEqualTo(gateBefore + 2);
        awaitOffline(device);
        List<String> types = new ArrayList<>();
        getJson("/api/intercom/logs/?building_id=%d&device_id=%d".formatted(building, device), guard)
                .get("results").forEach(row -> types.add(row.get("event_type").asString()));
        assertThat(types).contains("offline", "ring", "card_denied", "card_allowed", "online");
        assertThat(deviceRow(guard, building, device).get("online").asBoolean()).isFalse();
    }

    @Test
    void theAppReleasesTheDoorDownTheLiveConnection() throws Exception {
        String guard = tokenFor(GUARD);
        long device = northGate(guard);

        try (Panel panel = new Panel(gateway.port())) {
            assertThat(panel.say("HELLO " + device)).startsWith("OK");
            postJson("/api/intercom/devices/%d/open/".formatted(device), tokenFor(RESIDENT), null, 403);

            // A web request thread writes to the socket this panel's worker thread is reading from.
            JsonNode reply = postJson("/api/intercom/devices/%d/open/".formatted(device), guard, null, 200);
            assertThat(reply.get("detail").asString()).isEqualTo("Door released at North Gate Intercom.");
            assertThat(panel.readOrNull()).isEqualTo("OPEN 5 Jamal Uddin");
            assertThat(panel.say("PING")).isEqualTo("PONG"); // the connection carries on
            panel.say("BYE");
        }
        awaitOffline(device);
        postJson("/api/intercom/devices/%d/open/".formatted(device), guard, null, 400);
    }

    @Test
    void manyPanelsAreServedAtOnceEachOnItsOwnThread() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(tokenFor(GUARD));
        int panels = 10;
        List<Long> ids = new ArrayList<>();
        long seed = System.nanoTime();
        for (int i = 0; i < panels; i++) {
            String ip = "10.%d.%d.%d".formatted(Math.floorMod(seed >> 8, 250) + 1, Math.floorMod(seed >> 16, 250) + 1, i + 1);
            ids.add(postJson("/api/intercom/devices/", admin,
                    "{\"building\":%d,\"device_name\":\"Block panel %d\",\"ip_address\":\"%s\"}".formatted(building, i, ip), 201)
                    .get("id").asLong());
        }

        ExecutorService clients = Executors.newFixedThreadPool(panels);
        try {
            // Every panel connects at once and stays connected until all of them are in.
            CyclicBarrier allConnected = new CyclicBarrier(panels + 1);
            CountDownLatch done = new CountDownLatch(panels);
            List<Future<List<String>>> replies = new ArrayList<>();
            for (long id : ids) {
                replies.add(clients.submit(() -> {
                    try (Panel panel = new Panel(gateway.port())) {
                        List<String> got = new ArrayList<>();
                        got.add(panel.say("HELLO " + id));
                        got.add(panel.say("CARD GLH-AC-0001"));
                        got.add(panel.say("DOOR OPENED"));
                        allConnected.await(10, TimeUnit.SECONDS);
                        allConnected.await(10, TimeUnit.SECONDS); // held open while the test looks
                        got.add(panel.say("BYE"));
                        return got;
                    } finally {
                        done.countDown();
                    }
                }));
            }
            allConnected.await(10, TimeUnit.SECONDS);
            for (long id : ids) {
                assertThat(registry.status(id)).as("panel %d online", id).isPresent();
            }
            assertThat(gateway.activeConnections()).isGreaterThanOrEqualTo(panels);
            allConnected.await(10, TimeUnit.SECONDS);
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

            for (Future<List<String>> reply : replies) {
                List<String> got = reply.get();
                assertThat(got.get(0)).startsWith("OK Block panel");
                assertThat(got.subList(1, 4)).containsExactly("ALLOW Ayesha", "OK", "BYE");
            }
        } finally {
            clients.shutdownNow();
            for (long id : ids) {
                awaitOffline(id); // its worker thread writes the "offline" row as it winds down
            }
            deleteDevices(ids);
        }
    }

    @Test
    void aSilentPanelIsDroppedAndARunawayLineRefused() throws Exception {
        long device = northGate(tokenFor(GUARD));
        try (Panel silent = new Panel(gateway.port())) {
            assertThat(silent.say("HELLO " + device)).startsWith("OK");
            long started = System.nanoTime();
            assertThat(silent.readOrNull()).isEqualTo("ERR timeout"); // idle timeout is 3 s in tests
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(2500);
            assertThat(silent.readOrNull()).isNull();
        }
        awaitOffline(device);

        try (Panel chatty = new Panel(gateway.port())) {
            assertThat(chatty.say("HELLO " + device)).startsWith("OK");
            // One character over the limit, no newline: the server refuses it before the line ends.
            assertThat(chatty.sayRaw("x".repeat(DeviceConnection.MAX_LINE + 1))).isEqualTo("ERR line too long");
            assertThat(chatty.readOrNull()).isNull();
        }
        awaitOffline(device);
    }

    @Test
    void aReconnectingPanelReplacesItsOldConnection() throws Exception {
        long device = northGate(tokenFor(GUARD));
        try (Panel before = new Panel(gateway.port()); Panel after = new Panel(gateway.port())) {
            assertThat(before.say("HELLO " + device)).startsWith("OK");
            assertThat(after.say("HELLO " + device)).startsWith("OK");
            assertThat(before.readOrNull()).isNull(); // the old line was closed
            assertThat(registry.status(device)).isPresent();
            assertThat(after.say("PING")).isEqualTo("PONG");
            after.say("BYE");
        }
        awaitOffline(device);
    }

    @Test
    void panelsAreTrustedBySecretOrByTheirRegisteredAddress() throws Exception {
        InetAddress panel = InetAddress.getByName("192.168.10.25");
        InetAddress elsewhere = InetAddress.getByName("192.168.10.99");
        InetAddress local = InetAddress.getLoopbackAddress();

        assertThat(DeviceService.trusted("", null, panel, "192.168.10.25")).isTrue();
        assertThat(DeviceService.trusted("", null, local, "192.168.10.25")).isTrue();
        assertThat(DeviceService.trusted("", null, elsewhere, "192.168.10.25")).isFalse();

        assertThat(DeviceService.trusted("s3cret", "s3cret", elsewhere, "192.168.10.25")).isTrue();
        assertThat(DeviceService.trusted("s3cret", "guess", panel, "192.168.10.25")).isFalse();
        assertThat(DeviceService.trusted("s3cret", null, local, "192.168.10.25")).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    /** The seeded panel, found by its registered address rather than by scanning a page of devices. */
    private long northGate(String token) throws Exception {
        return devices.findByBuildingIdAndIpAddress(buildingId(token), "192.168.10.25")
                .orElseThrow(() -> new IllegalStateException("The seeded North Gate Intercom is missing"))
                .getId();
    }

    /** Removes panels a test registered, with their log rows, so repeated runs don't pile them up. */
    private void deleteDevices(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("delete from IntercomLog l where l.device.id in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from IntercomDevice d where d.id in :ids").setParameter("ids", ids).executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private JsonNode deviceRow(String token, long building, long device) throws Exception {
        return getJson("/api/intercom/devices/%d/".formatted(device), token);
    }

    /** The panel's worker thread unregisters it as it winds down, a moment after the socket closes. */
    private void awaitOffline(long device) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (registry.status(device).isPresent() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(registry.status(device)).as("device %d offline", device).isEmpty();
    }

    /** A panel: one TCP connection speaking the line protocol. */
    private static final class Panel implements AutoCloseable {

        private final Socket socket;
        private final BufferedReader in;
        private final Writer out;

        Panel(int port) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), port);
            socket.setSoTimeout(8000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        }

        String say(String line) throws IOException {
            return sayRaw(line + "\n");
        }

        String sayRaw(String text) throws IOException {
            out.write(text);
            out.flush();
            return readOrNull();
        }

        /** The next line from the server, or null once it has closed the connection. */
        String readOrNull() throws IOException {
            try {
                return in.readLine();
            } catch (SocketException closed) {
                return null;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
