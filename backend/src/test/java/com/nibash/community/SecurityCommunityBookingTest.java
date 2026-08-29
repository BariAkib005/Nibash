package com.nibash.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.seed.DemoSeeder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Week 4 definition of done (plan §Week 4): the gate flow, one-vote-per-resident, and the booking
 * conflict rules — including the two that only show up when requests race.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityCommunityBookingTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoSeeder seeder;

    private static boolean seeded = false;

    @BeforeAll
    static void resetFlag() {
        seeded = false;
    }

    // ---------------------------------------------------------------- helpers

    private void ensureSeeded() {
        if (!seeded) {
            seeder.seed();
            seeded = true;
        }
    }

    private String tokenFor(String email) throws Exception {
        ensureSeeded();
        String body = mvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"%s","password":"%s"}
                                 """.formatted(email, DemoSeeder.DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asString();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Token " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode postJson(String path, String token, String payload, int expectedStatus) throws Exception {
        String body = mvc.perform(post(path)
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    private long buildingId(String token) throws Exception {
        return getJson("/api/auth/me/", token).get("building").get("id").asLong();
    }

    /** The resident row belonging to {@code email} in their own building. */
    private long residentIdOf(String email) throws Exception {
        String token = tokenFor(email);
        long building = buildingId(token);
        JsonNode page = getJson("/api/residents/?building_id=" + building, token);
        String name = getJson("/api/auth/me/", token).get("user").get("name").asString();

        for (JsonNode row : page.get("results")) {
            if (name.equals(row.get("resident_name").asString())) {
                return row.get("id").asLong();
            }
        }
        throw new IllegalStateException("No resident row for " + email);
    }

    // ---------------------------------------------------------------- visitor scan

    @Test
    void scanOfAPastDatedAppointmentIsRejected() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        String guard = tokenFor("guard1@nibash.bd");
        long resident = residentIdOf("resident1@nibash.bd");

        JsonNode expired = postJson("/api/appointments/", admin, """
                {"resident":%d,"visitor_name":"Late Courier","visitor_phone":"+8801700000001",
                 "scheduled_time":"%s"}
                """.formatted(resident, LocalDate.now().minusDays(1).atTime(10, 0).format(ISO)), 201);

        mvc.perform(post("/api/visitors/scan/")
                        .header("Authorization", "Token " + guard)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qr_token\":\"%s\"}".formatted(expired.get("qr_token").asString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Appointment has expired"));
    }

    @Test
    void sameDayLateScanStillPassesAndIsIdempotent() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        String guard = tokenFor("guard1@nibash.bd");
        long resident = residentIdOf("resident1@nibash.bd");

        // Scheduled earlier today: the rule is by date, not by clock time.
        JsonNode appointment = postJson("/api/appointments/", admin, """
                {"resident":%d,"visitor_name":"Nafis Ahmed","visitor_phone":"+8801700000002",
                 "scheduled_time":"%s"}
                """.formatted(resident, LocalDate.now().atTime(0, 1).format(ISO)), 201);

        String token = appointment.get("qr_token").asString();
        JsonNode first = postJson("/api/visitors/scan/", guard,
                "{\"qr_token\":\"%s\"}".formatted(token), 200);

        assertThat(first.get("status").asString()).isEqualTo("checked_in");
        assertThat(first.get("checkin_time").isNull()).isFalse();

        // Re-scanning the same pass must not create a second visitor or move the arrival time.
        JsonNode second = postJson("/api/visitors/scan/", guard,
                "{\"qr_token\":\"%s\"}".formatted(token), 200);

        assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(second.get("checkin_time").asString()).isEqualTo(first.get("checkin_time").asString());

        mvc.perform(patch("/api/visitors/" + first.get("id").asLong() + "/checkout/")
                        .header("Authorization", "Token " + guard)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("checked_out"))
                .andExpect(jsonPath("$.checkout_time").isNotEmpty());
    }

    @Test
    void aTokenFromAnotherBuildingIsNotFound() throws Exception {
        String admin2 = tokenFor("admin2@nibash.bd");        // Banani
        String guard1 = tokenFor("guard1@nibash.bd");        // Gulshan
        long bananiResident = residentIdOf("committee2@nibash.bd");

        JsonNode foreign = postJson("/api/appointments/", admin2, """
                {"resident":%d,"visitor_name":"Wrong Gate","visitor_phone":"+8801700000003",
                 "scheduled_time":"%s"}
                """.formatted(bananiResident, LocalDate.now().atTime(9, 0).format(ISO)), 201);

        mvc.perform(post("/api/visitors/scan/")
                        .header("Authorization", "Token " + guard1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qr_token\":\"%s\"}".formatted(foreign.get("qr_token").asString())))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- SOS

    @Test
    void sosCreateAlsoWritesANotification() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        long residentId = residentIdOf("resident1@nibash.bd");
        long building = buildingId(resident);

        long before = getJson("/api/notifications/?building_id=" + building, resident).get("count").asLong();

        postJson("/api/emergencies/", resident, """
                {"resident":%d,"latitude":"23.780636","longitude":"90.419325"}
                """.formatted(residentId), 201);

        JsonNode after = getJson("/api/notifications/?building_id=" + building, resident);
        assertThat(after.get("count").asLong()).isEqualTo(before + 1);

        JsonNode newest = after.get("results").get(0);
        assertThat(newest.get("type").asString()).isEqualTo("sos");
        assertThat(newest.get("message").asString())
                .isEqualTo("Emergency SOS alert created. Security has been notified.");
    }

    // ---------------------------------------------------------------- polls

    @Test
    void aResidentVotesOnceAndCannotVoteAsSomeoneElse() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        String resident = tokenFor("resident1@nibash.bd");
        long building = buildingId(admin);
        long myResidentId = residentIdOf("resident1@nibash.bd");
        long someoneElse = residentIdOf("resident2@nibash.bd");

        JsonNode poll = postJson("/api/polls/", admin, """
                {"building":%d,"question":"Repaint the lobby?","end_date":"%s",
                 "options":[{"option_text":"Yes"},{"option_text":"No"}]}
                """.formatted(building, LocalDateTime.now().plusDays(7).format(ISO)), 201);

        long pollId = poll.get("id").asLong();
        long optionId = poll.get("options").get(0).get("id").asLong();

        // Anti-spoofing: the body names another resident, but the vote lands on the caller's row.
        JsonNode vote = postJson("/api/polls/" + pollId + "/vote/", resident, """
                {"option_id":%d,"resident_id":%d}
                """.formatted(optionId, someoneElse), 201);

        assertThat(vote.get("resident").asLong()).isEqualTo(myResidentId);

        mvc.perform(post("/api/polls/" + pollId + "/vote/")
                        .header("Authorization", "Token " + resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option_id\":%d,\"resident_id\":%d}".formatted(optionId, myResidentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Already voted"));

        JsonNode results = getJson("/api/polls/" + pollId + "/results/", admin);
        assertThat(results.get("total_votes").asLong()).isEqualTo(1);
        assertThat(results.get("results").get(0).get("percentage").asDouble()).isEqualTo(100.00);
        assertThat(results.get("results").get(1).get("percentage").asDouble()).isEqualTo(0.00);
    }

    @Test
    void votingOnAClosedPollIsRejected() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        String resident = tokenFor("resident1@nibash.bd");
        long building = buildingId(admin);
        long residentId = residentIdOf("resident1@nibash.bd");

        JsonNode poll = postJson("/api/polls/", admin, """
                {"building":%d,"question":"Closed already?","start_date":"%s","end_date":"%s",
                 "options":[{"option_text":"Yes"}]}
                """.formatted(building,
                LocalDateTime.now().minusDays(9).format(ISO),
                LocalDateTime.now().minusDays(1).format(ISO)), 201);

        mvc.perform(post("/api/polls/" + poll.get("id").asLong() + "/vote/")
                        .header("Authorization", "Token " + resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option_id\":%d,\"resident_id\":%d}"
                                .formatted(poll.get("options").get(0).get("id").asLong(), residentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Poll has closed"));
    }

    // ---------------------------------------------------------------- bookings

    @Test
    void endTimeMustBeAfterStartTime() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long resourceId = newResource(admin, "Quiet Room");
        long residentId = residentIdOf("resident1@nibash.bd");
        LocalDateTime start = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);

        mvc.perform(post("/api/bookings/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"resource":%d,"resident":%d,"start_time":"%s","end_time":"%s"}
                                 """.formatted(resourceId, residentId,
                                start.format(ISO), start.minusHours(1).format(ISO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.end_time[0]").value("End time must be after start time."));
    }

    @Test
    void overlappingBookingIsRejectedAndQuotePreviewsIt() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long resourceId = newResource(admin, "Rooftop Lounge");
        long residentId = residentIdOf("resident1@nibash.bd");

        LocalDateTime start = uniqueFutureSlot();
        String window = """
                        {"resource":%d,"resident":%d,"start_time":"%s","end_time":"%s"}
                        """.formatted(resourceId, residentId, start.format(ISO), start.plusHours(2).format(ISO));

        // The quote is clean before anything is booked.
        postJson("/api/bookings/quote/", admin, window, 200);
        postJson("/api/bookings/", admin, window, 201);

        // A window that starts inside the existing one overlaps, even though it starts later.
        String overlapping = """
                             {"resource":%d,"resident":%d,"start_time":"%s","end_time":"%s"}
                             """.formatted(resourceId, residentId,
                start.plusHours(1).format(ISO), start.plusHours(3).format(ISO));

        mvc.perform(post("/api/bookings/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(overlapping))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.non_field_errors[0]")
                        .value("This resource already has a booking in that time window."));

        // And the calendar learns about it before submit, via the same rule.
        mvc.perform(post("/api/bookings/quote/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(overlapping))
                .andExpect(status().isBadRequest());

        // A window that merely touches the end is not an overlap.
        postJson("/api/bookings/quote/", admin, """
                {"resource":%d,"resident":%d,"start_time":"%s","end_time":"%s"}
                """.formatted(resourceId, residentId,
                start.plusHours(2).format(ISO), start.plusHours(3).format(ISO)), 200);
    }

    @Test
    void twoConcurrentBookingsOfTheSameSlotLeaveExactlyOneWinner() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long resourceId = newResource(admin, "Community Hall");
        long residentId = residentIdOf("resident1@nibash.bd");

        LocalDateTime start = uniqueFutureSlot();
        String payload = """
                         {"resource":%d,"resident":%d,"start_time":"%s","end_time":"%s"}
                         """.formatted(resourceId, residentId, start.format(ISO), start.plusHours(2).format(ISO));

        int threads = 2;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Callable<Void> attempt = () -> {
            startTogether.await();
            int status = mvc.perform(post("/api/bookings/")
                            .header("Authorization", "Token " + admin)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andReturn().getResponse().getStatus();
            if (status == 201) {
                created.incrementAndGet();
            } else if (status == 400) {
                rejected.incrementAndGet();
            }
            return null;
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (Future<Void> result : pool.invokeAll(List.of(attempt, attempt))) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(created.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);

        JsonNode availability = getJson("/api/resources/" + resourceId + "/availability/?start_from="
                + start.minusHours(1).format(ISO) + "&end_to=" + start.plusHours(6).format(ISO), admin);
        assertThat(availability.get("bookings")).hasSize(1);
    }

    // ---------------------------------------------------------------- notices, events, gate

    @Test
    void noticeBoardHidesExpiredNoticesUnlessAsked() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = buildingId(admin);
        String marker = "Archived lift notice " + System.nanoTime();

        postJson("/api/notices/", admin, """
                {"building":%d,"title":"%s","body":"Lift 2 was out of service last month.",
                 "publish_date":"%s","expiry_date":"%s"}
                """.formatted(building, marker,
                LocalDateTime.now().minusDays(40).format(ISO),
                LocalDateTime.now().minusDays(10).format(ISO)), 201);

        JsonNode live = getJson("/api/notices/?building_id=" + building + "&search=" + "Archived", admin);
        assertThat(live.get("count").asLong()).isZero();

        JsonNode archived = getJson(
                "/api/notices/?building_id=" + building + "&search=Archived&include_archived=true", admin);
        assertThat(archived.get("count").asLong()).isPositive();
    }

    @Test
    void rsvpUpsertsRatherThanDuplicating() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = buildingId(admin);

        JsonNode event = postJson("/api/events/", admin, """
                {"building":%d,"title":"Eid Milad","description":"Community lunch","event_date":"%s"}
                """.formatted(building, LocalDateTime.now().plusDays(14).format(ISO)), 201);
        long eventId = event.get("id").asLong();

        String resident = tokenFor("resident1@nibash.bd");
        JsonNode first = postJson("/api/events/" + eventId + "/rsvp/", resident,
                "{\"status\":\"going\"}", 200);
        JsonNode changed = postJson("/api/events/" + eventId + "/rsvp/", resident,
                "{\"status\":\"not_going\"}", 200);

        assertThat(changed.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(changed.get("status").asString()).isEqualTo("not_going");

        JsonNode attendees = getJson("/api/events/" + eventId + "/attendees/", admin);
        assertThat(attendees.get("results")).hasSize(1);
    }

    @Test
    void gateAnalyticsBucketsByHourAndType() throws Exception {
        String guard = tokenFor("guard1@nibash.bd");
        long building = buildingId(guard);

        postJson("/api/gate-events/", guard,
                "{\"building\":%d,\"event_type\":\"open\"}".formatted(building), 201);
        postJson("/api/gate-events/", guard,
                "{\"building\":%d,\"event_type\":\"close\"}".formatted(building), 201);

        mvc.perform(post("/api/gate-events/")
                        .header("Authorization", "Token " + guard)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"building\":%d,\"event_type\":\"sideways\"}".formatted(building)))
                .andExpect(status().isBadRequest());

        JsonNode analytics = getJson("/api/gate-events/analytics/?building_id=" + building, guard);
        assertThat(analytics.get("results")).isNotEmpty();

        JsonNode bucket = analytics.get("results").get(0);
        assertThat(bucket.get("hour").asInt()).isBetween(0, 23);
        assertThat(bucket.get("total").asLong()).isPositive();

        // The two events above were logged just now, so a bucket must exist for the current hour
        // *in the building's timezone*. Timestamps are stored UTC, so without the shift this lands
        // six hours out and the "busiest hour" chart would be quietly wrong.
        int localHour = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Dhaka")).getHour();
        boolean hasCurrentHour = false;
        for (JsonNode row : analytics.get("results")) {
            if (row.get("hour").asInt() == localHour) {
                hasCurrentHour = true;
            }
        }
        assertThat(hasCurrentHour)
                .as("analytics should bucket by Asia/Dhaka hour %d, not the stored UTC hour", localHour)
                .isTrue();
    }

    // ---------------------------------------------------------------- fixtures

    private long newResource(String token, String name) throws Exception {
        long building = buildingId(token);
        JsonNode created = postJson("/api/resources/", token, """
                {"building":%d,"name":"%s %d","capacity":40,"type":"hall"}
                """.formatted(building, name, System.nanoTime()), 201);
        return created.get("id").asLong();
    }

    /** A slot far enough out that no other run or fixture has claimed it. */
    private static LocalDateTime uniqueFutureSlot() {
        return LocalDateTime.now()
                .plusDays(60 + (System.nanoTime() % 500))
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
    }
}
