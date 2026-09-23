package com.nibash.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nibash.seed.DemoSeeder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Week 5 definition of done: "two WebSocket clients in one room both receive a broadcast" — run
 * against a real server on a random port, with the JDK's own WebSocket client — plus the
 * handshake hardening (spec §15.9).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatSocketTest {

    @Value("${local.server.port}") int port;
    @Autowired DemoSeeder seeder;
    @Autowired ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void twoClientsInOneRoomBothReceiveAPersistedMessage() throws Exception {
        seeder.seed();
        String ayesha = login("resident1@nibash.bd");
        String farhana = login("committee1@nibash.bd");
        long room = roomNamed(ayesha, "General");

        Listener a = new Listener();
        Listener b = new Listener();
        WebSocket socketA = connect(room, ayesha, a);
        WebSocket socketB = connect(room, farhana, b);

        String text = "socket test " + System.nanoTime();
        HttpResponse<String> posted = http.send(HttpRequest.newBuilder(uri("/api/chat/messages/"))
                .header("Authorization", "Token " + ayesha)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"room\":%d,\"content\":\"%s\"}".formatted(room, text)))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(posted.statusCode()).isEqualTo(201);

        JsonNode frameA = a.awaitMessage(text);
        JsonNode frameB = b.awaitMessage(text);
        assertThat(frameA.get("message").get("sender_name").asString()).isEqualTo("Ayesha Rahman");
        assertThat(frameB.get("message").get("id").asLong()).isEqualTo(frameA.get("message").get("id").asLong());

        // The spec's echo contract: an inbound "message" frame reaches everyone, sender included,
        // with the sender set by the server rather than the client.
        socketB.sendText("{\"type\":\"message\",\"text\":\"echo\",\"sender\":\"Someone Else\"}", true).join();
        JsonNode echoed = a.await(frame -> "message".equals(frame.path("type").asString()));
        assertThat(echoed.get("sender").asString()).isEqualTo("Farhana Haque");

        socketA.abort();
        socketB.abort();
    }

    @Test
    void handshakeRejectsBadTokensAndOutsidersOfPrivateRooms() throws Exception {
        seeder.seed();
        String rafiq = login("resident2@nibash.bd");
        String farhana = login("committee1@nibash.bd");
        long general = roomNamed(rafiq, "General");
        long committee = roomNamed(farhana, "Committee");

        assertThatThrownBy(() -> connect(general, "not-a-token", new Listener()))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect(committee, rafiq, new Listener()))
                .isInstanceOf(ExecutionException.class);
        // A member of the committee room gets in.
        connect(committee, farhana, new Listener()).abort();
    }

    // ---------------------------------------------------------------- helpers

    private WebSocket connect(long room, String token, Listener listener) throws Exception {
        return http.newWebSocketBuilder()
                .header("Origin", "http://127.0.0.1:5173")
                .buildAsync(URI.create("ws://localhost:%d/ws/chat/%d/?token=%s".formatted(port, room, token)), listener)
                .get(5, TimeUnit.SECONDS);
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/auth/login/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, DemoSeeder.DEMO_PASSWORD)))
                .build(), HttpResponse.BodyHandlers.ofString());
        return json.readTree(response.body()).get("token").asString();
    }

    private long roomNamed(String token, String name) throws Exception {
        long building = json.readTree(get("/api/auth/me/", token)).get("building").get("id").asLong();
        for (JsonNode room : json.readTree(get("/api/chat/rooms/?building_id=" + building, token)).get("results")) {
            if (name.equals(room.get("name").asString())) {
                return room.get("id").asLong();
            }
        }
        throw new IllegalStateException("No room " + name);
    }

    private String get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Token " + token).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** Collects text frames; {@link #await} waits for one matching a predicate. */
    private class Listener implements WebSocket.Listener {

        private final List<JsonNode> frames = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                frames.add(json.readTree(partial.toString()));
                partial.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        JsonNode awaitMessage(String content) throws InterruptedException {
            return await(frame -> "message.created".equals(frame.path("type").asString())
                    && content.equals(frame.path("message").path("content").asString()));
        }

        JsonNode await(java.util.function.Predicate<JsonNode> match) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                for (JsonNode frame : frames) {
                    if (match.test(frame)) {
                        return frame;
                    }
                }
                Thread.sleep(25);
            }
            throw new AssertionError("No matching frame within 5s; got " + frames);
        }
    }
}
