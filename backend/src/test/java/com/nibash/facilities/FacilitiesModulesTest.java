package com.nibash.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.support.ApiTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * Week 5 definition of done (plan §Week 5) plus the Week 6 fixes: the long-tail modules, their
 * custom actions, and the rules added on top of the spec.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FacilitiesModulesTest extends ApiTestSupport {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String ADMIN = "admin1@nibash.bd";
    private static final String COMMITTEE = "committee1@nibash.bd";
    private static final String RESIDENT = "resident1@nibash.bd";
    private static final String TENANT = "resident2@nibash.bd";
    private static final String GUARD = "guard1@nibash.bd";

    // ---------------------------------------------------------------- vendors

    @Test
    void nearbyReturnsOnlyVendorsInsideTheRadiusNearestFirst() throws Exception {
        String admin = tokenFor(ADMIN);
        long service = serviceId(admin, "Plumbing");

        JsonNode within5 = getJson("/api/vendors/nearby/?service_id=%d&lat=23.7925&lng=90.4078&radius_km=5"
                .formatted(service), admin);
        List<String> names = names(within5.get("results"));
        assertThat(names).contains("Rahman Plumbing Works").doesNotContain("Mirpur Pipe Fixers");
        for (JsonNode vendor : within5.get("results")) {
            assertThat(vendor.get("distance_km").asDouble()).isLessThanOrEqualTo(5.0);
        }

        JsonNode within10 = getJson("/api/vendors/nearby/?service_id=%d&lat=23.7925&lng=90.4078&radius_km=10"
                .formatted(service), admin);
        List<Double> distances = new ArrayList<>();
        within10.get("results").forEach(v -> distances.add(v.get("distance_km").asDouble()));
        assertThat(names(within10.get("results"))).contains("Mirpur Pipe Fixers");
        assertThat(distances).isSorted();

        JsonNode missing = getJson("/api/vendors/nearby/?lat=23.7", admin, 400);
        assertThat(missing.get("detail").asString()).isEqualTo("service_id, lat, lng are required");
    }

    @Test
    void reviewIsFiledAsTheCallerAndMovesTheVendorAverage() throws Exception {
        String admin = tokenFor(ADMIN);
        String tenant = tokenFor(TENANT);
        long service = serviceId(admin, "Electrical");
        long building = buildingId(admin);
        JsonNode vendor = postJson("/api/vendors/", admin,
                "{\"service\":%d,\"building\":%d,\"name\":\"%s\",\"rating\":\"3.0\"}"
                        .formatted(service, building, unique("Test Electric")), 201);
        long vendorId = vendor.get("id").asLong();

        // Claiming to be another resident is ignored — the review is the caller's own.
        long someoneElse = residentIdOf(tokenFor(RESIDENT));
        JsonNode review = postJson("/api/reviews/", tenant,
                "{\"vendor\":%d,\"resident\":%d,\"rating\":5,\"comment\":\"great\"}".formatted(vendorId, someoneElse), 201);
        assertThat(review.get("resident").asLong()).isEqualTo(residentIdOf(tenant));

        JsonNode refreshed = getJson("/api/vendors/" + vendorId + "/", admin);
        assertThat(refreshed.get("rating").asDouble()).isEqualTo(5.0);

        postJson("/api/reviews/", tenant, "{\"vendor\":%d,\"rating\":9}".formatted(vendorId), 400);
    }

    @Test
    void onlyBackOfficeManagesGlobalVendors() throws Exception {
        String committee = tokenFor(COMMITTEE);
        long service = serviceId(committee, "Cleaning");
        postJson("/api/vendors/", committee,
                "{\"service\":%d,\"name\":\"%s\"}".formatted(service, unique("Global Cleaner")), 403);
        postJson("/api/vendors/", tokenFor(ADMIN),
                "{\"service\":%d,\"name\":\"%s\"}".formatted(service, unique("Global Cleaner")), 201);
    }

    // ---------------------------------------------------------------- documents

    @Test
    void downloadWritesExactlyOneAuditRow() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        JsonNode doc = uploadDocument(admin, building, unique("Minutes"), null, "minutes.pdf");
        long id = doc.get("id").asLong();

        int before = countEvents(getJson("/api/documents/" + id + "/audit/", admin), "download");
        JsonNode download = getJson("/api/documents/" + id + "/download/", admin);
        int after = countEvents(getJson("/api/documents/" + id + "/audit/", admin), "download");

        assertThat(after - before).isEqualTo(1);
        assertThat(download.get("file_path").asString()).startsWith("documents/");
        // Every create writes an edit row too.
        assertThat(countEvents(getJson("/api/documents/" + id + "/audit/", admin), "edit")).isEqualTo(1);
    }

    @Test
    void newVersionContinuesTheChainAndRetiresThePredecessor() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        String title = unique("Fire plan");
        long v1 = uploadDocument(admin, building, title, null, "plan-v1.pdf").get("id").asLong();
        JsonNode v2 = uploadDocument(admin, building, title, v1, "plan-v2.pdf");

        assertThat(v2.get("version").asInt()).isEqualTo(2);
        assertThat(getJson("/api/documents/" + v1 + "/", admin).get("is_active").asBoolean()).isFalse();
        JsonNode chain = getJson("/api/documents/" + v1 + "/versions/", admin);
        assertThat(chain).hasSize(2);
        assertThat(chain.get(1).get("id").asLong()).isEqualTo(v2.get("id").asLong());

        // Only the latest version can be built on, and the old one cannot be deleted under it.
        mvc.perform(multipart("/api/documents/")
                        .file(new MockMultipartFile("file", "again.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("title", title).param("building", String.valueOf(building)).param("parent", String.valueOf(v1))
                        .header("Authorization", "Token " + admin))
                .andExpect(status().isBadRequest());
        deleteJson("/api/documents/" + v1 + "/", admin, 400);
    }

    @Test
    void uploadsOutsideTheAllowlistAreRejectedAndMediaIsServedSafely() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        mvc.perform(multipart("/api/documents/")
                        .file(new MockMultipartFile("file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes()))
                        .param("title", "evil").param("building", String.valueOf(building))
                        .header("Authorization", "Token " + admin))
                .andExpect(status().isBadRequest());

        JsonNode doc = uploadDocument(admin, building, unique("Served"), null, "served.pdf");
        mvc.perform(get("/media/" + doc.get("file_path").asString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")));
        mvc.perform(get("/media/documents/definitely-not-here.pdf")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- chat

    @Test
    void messagesNotifyEveryOtherMemberAndPrivateRoomsStayPrivate() throws Exception {
        String resident = tokenFor(RESIDENT);
        String committee = tokenFor(COMMITTEE);
        String tenant = tokenFor(TENANT);
        long building = buildingId(resident);

        long general = roomId(committee, building, "General");
        String text = unique("hello neighbours");
        JsonNode message = postJson("/api/chat/messages/", resident,
                "{\"room\":%d,\"content\":\"%s\"}".formatted(general, text), 201);
        assertThat(message.get("sender_name").asString()).isEqualTo("Ayesha Rahman");

        JsonNode search = getJson("/api/chat/messages/?room_id=%d&search=%s&latest=true".formatted(general, text), resident);
        assertThat(search.get("count").asInt()).isEqualTo(1);

        // Side effect: every other member gets a chat notification; the sender does not.
        long farhana = residentIdOf(committee);
        long ayesha = residentIdOf(resident);
        JsonNode feed = getJson("/api/notifications/?building_id=" + building, committee);
        boolean farhanaNotified = false;
        boolean senderNotifiedForThis = false;
        for (JsonNode n : feed.get("results")) {
            if ("chat".equals(n.get("type").asString()) && n.get("sent_at").asString().compareTo(message.get("sent_at").asString()) >= 0) {
                farhanaNotified |= n.get("resident").asLong() == farhana;
                senderNotifiedForThis |= n.get("resident").asLong() == ayesha;
            }
        }
        assertThat(farhanaNotified).isTrue();
        assertThat(senderNotifiedForThis).isFalse();

        JsonNode summary = getJson("/api/chat/messages/summary/?building_id=" + building, committee);
        assertThat(summary.get("rooms").asInt()).isEqualTo(getJson("/api/chat/rooms/?building_id=" + building, committee).get("count").asInt());

        long committeeRoom = roomId(committee, building, "Committee");
        getJson("/api/chat/rooms/" + committeeRoom + "/", tenant, 404);
        postJson("/api/chat/messages/", tenant, "{\"room\":%d,\"content\":\"let me in\"}".formatted(committeeRoom), 404);
        assertThat(names(getJson("/api/chat/rooms/?building_id=" + building, tenant).get("results")))
                .doesNotContain("Committee");

        // Guards have no resident row, so they can read public rooms but not post.
        postJson("/api/chat/messages/", tokenFor(GUARD), "{\"room\":%d,\"content\":\"hi\"}".formatted(general), 400);
    }

    // ---------------------------------------------------------------- parking

    @Test
    void layoutClampsIsIdempotentAndAssignmentKeepsSlotsHonest() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        String prefix = "T" + (System.nanoTime() % 100000);

        JsonNode first = postJson("/api/parking/layout/", admin,
                "{\"building_id\":%d,\"rows\":40,\"columns\":0,\"prefix\":\"%s\"}".formatted(building, prefix), 201);
        assertThat(first.get("layout").get("rows").asInt()).isEqualTo(12);
        assertThat(first.get("layout").get("columns").asInt()).isEqualTo(1);
        assertThat(first.get("slots")).hasSize(12);
        assertThat(first.get("slots").get(0).get("slot_number").asString()).isEqualTo(prefix + "1-01");

        JsonNode again = postJson("/api/parking/layout/", admin,
                "{\"building_id\":%d,\"rows\":12,\"columns\":1,\"prefix\":\"%s\"}".formatted(building, prefix), 201);
        assertThat(again.get("slots").get(0).get("id").asLong()).isEqualTo(first.get("slots").get(0).get("id").asLong());

        long slot = first.get("slots").get(0).get("id").asLong();
        long car = postJson("/api/vehicles/", admin, "{\"resident\":%d,\"vehicle_number\":\"%s\"}"
                .formatted(residentIdOf(tokenFor(RESIDENT)), unique("DHA")), 201).get("id").asLong();
        long bike = postJson("/api/vehicles/", admin, "{\"resident\":%d,\"vehicle_number\":\"%s\",\"type\":\"motorbike\"}"
                .formatted(residentIdOf(tokenFor(RESIDENT)), unique("DHB")), 201).get("id").asLong();

        patchJson("/api/vehicles/" + car + "/", admin, "{\"parking_slot\":%d}".formatted(slot), 200);
        assertThat(getJson("/api/parking/slots/" + slot + "/", admin).get("status").asString()).isEqualTo("occupied");
        patchJson("/api/vehicles/" + bike + "/", admin, "{\"parking_slot\":%d}".formatted(slot), 400);
        deleteJson("/api/parking/slots/" + slot + "/", admin, 400);

        patchJson("/api/vehicles/" + car + "/", admin, "{\"parking_slot\":null}", 200);
        assertThat(getJson("/api/parking/slots/" + slot + "/", admin).get("status").asString()).isEqualTo("available");

        // Residents register vehicles but do not allocate bays.
        String resident = tokenFor(RESIDENT);
        long own = postJson("/api/vehicles/", resident, "{\"vehicle_number\":\"%s\"}".formatted(unique("DHC")), 201)
                .get("id").asLong();
        patchJson("/api/vehicles/" + own + "/", resident, "{\"parking_slot\":%d}".formatted(slot), 403);
    }

    // ---------------------------------------------------------------- utilities & finance fixes

    @Test
    void aUtilityBillIsInvoicedOnceAndSettledWithItsInvoice() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        long resident = residentIdOf(tokenFor(RESIDENT));
        JsonNode residentRow = getJson("/api/residents/" + resident + "/", admin);
        long unit = residentRow.get("unit").asLong();

        long meter = meterFor(admin, building, unit);
        long bill = postJson("/api/utility-bills/", admin, "{\"meter\":%d,\"reading_date\":\"%s\",\"amount\":\"777.00\"}"
                .formatted(meter, LocalDate.now().minusDays(3)), 201).get("id").asLong();

        long billType = getJson("/api/bill-types/", admin).get("results").get(0).get("id").asLong();
        // Two far-future months so these invoice numbers are new on every run.
        int year = freshYear();
        List<Long> first = generate(admin, building, billType, year + "-01");
        List<Long> second = generate(admin, building, billType, year + "-02");

        JsonNode firstInvoice = invoiceFor(admin, first, resident);
        JsonNode secondInvoice = invoiceFor(admin, second, resident);
        assertThat(utilityItems(firstInvoice, bill)).isEqualTo(1);
        assertThat(utilityItems(secondInvoice, bill)).isZero();
        assertThat(getJson("/api/utility-bills/" + bill + "/", admin).get("status").asString()).isEqualTo("billed");

        postJson("/api/payments/checkout/", admin, "{\"invoice_id\":%d}".formatted(firstInvoice.get("id").asLong()), 201);
        assertThat(getJson("/api/utility-bills/" + bill + "/", admin).get("status").asString()).isEqualTo("paid");
    }

    /**
     * Regression: the expenses screen always submits multipart, and a single handler taking both
     * {@code @RequestBody} and {@code @RequestPart} rejected every such request with 415.
     */
    @Test
    void expensesCanBeRecordedFromTheFormWithOrWithoutAReceipt() throws Exception {
        String admin = tokenFor(ADMIN);
        String building = String.valueOf(buildingId(admin));
        String today = LocalDate.now().toString();

        String withReceipt = mvc.perform(multipart("/api/expenses/")
                        .file(new MockMultipartFile("receipt", "fuel.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("building", building).param("category", "Generator Fuel")
                        .param("amount", "2400").param("date", today)
                        .header("Authorization", "Token " + admin))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(withReceipt).get("receipt_path").asString()).startsWith("receipts/");

        mvc.perform(multipart("/api/expenses/")
                        .param("building", building).param("category", "Cleaning")
                        .param("amount", "900").param("date", today)
                        .header("Authorization", "Token " + admin))
                .andExpect(status().isCreated());

        postJson("/api/expenses/", admin, "{\"building\":%s,\"category\":\"Security\",\"amount\":\"500\",\"date\":\"%s\"}"
                .formatted(building, today), 201);

        mvc.perform(multipart("/api/expenses/")
                        .file(new MockMultipartFile("receipt", "x.exe", "application/octet-stream", new byte[] {1}))
                        .param("building", building).param("category", "Bad")
                        .param("amount", "1").param("date", today)
                        .header("Authorization", "Token " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void utilityGenerateIsIdempotentPerMonth() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        String month = freshYear() + "-06";
        JsonNode first = postJson("/api/utility-bills/generate/", admin,
                "{\"building_id\":%d,\"month\":\"%s\"}".formatted(building, month), 201);
        JsonNode second = postJson("/api/utility-bills/generate/", admin,
                "{\"building_id\":%d,\"month\":\"%s\"}".formatted(building, month), 201);
        assertThat(first.get("created_bills").size()).isPositive();
        assertThat(second.get("created_bills").size()).isZero();
        postJson("/api/utility-bills/generate/", admin, "{\"building_id\":%d}".formatted(building), 400);
    }

    // ---------------------------------------------------------------- visitor fixes

    @Test
    void aPassOnlyOpensTheGateOnItsDayAndCheckedInVisitorsCannotBeCancelled() throws Exception {
        String resident = tokenFor(RESIDENT);
        String guard = tokenFor(GUARD);
        long me = residentIdOf(resident);

        JsonNode future = postJson("/api/appointments/", resident, "{\"resident\":%d,\"visitor_name\":\"%s\",\"scheduled_time\":\"%s\"}"
                .formatted(me, unique("Early"), LocalDateTime.now().plusDays(3).withNano(0).format(ISO)), 201);
        JsonNode early = postJson("/api/visitors/scan/", guard,
                "{\"qr_token\":\"%s\"}".formatted(future.get("qr_token").asString()), 400);
        assertThat(early.get("detail").asString()).contains("isn't valid yet");

        JsonNode today = postJson("/api/appointments/", resident, "{\"resident\":%d,\"visitor_name\":\"%s\",\"scheduled_time\":\"%s\"}"
                .formatted(me, unique("Today"), LocalDate.now().atTime(23, 0).format(ISO)), 201);
        postJson("/api/visitors/scan/", guard, "{\"qr_token\":\"%s\"}".formatted(today.get("qr_token").asString()), 200);

        JsonNode refused = deleteJson("/api/appointments/" + today.get("id").asLong() + "/", resident, 400);
        assertThat(refused.get("detail").asString()).contains("already checked in");
        deleteJson("/api/appointments/" + future.get("id").asLong() + "/", resident, 204);
    }

    // ---------------------------------------------------------------- assets, lifts, waste

    @Test
    void liftsCurrentKeepsTheLatestLogPerLiftAndWarrantiesAreGraded() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        long lift = postJson("/api/assets/", admin, "{\"building\":%d,\"name\":\"%s\",\"type\":\"Passenger lift\",\"warranty_expiry\":\"%s\"}"
                .formatted(building, unique("Lift"), LocalDate.now().plusDays(10)), 201).get("id").asLong();
        assertThat(getJson("/api/assets/" + lift + "/", admin).get("warranty_state").asString()).isEqualTo("expiring");

        String staff = tokenFor("staff2@nibash.bd");
        postJson("/api/lifts/status/", staff, "{\"building\":%d,\"asset\":%d,\"status\":\"operational\",\"timestamp\":\"%s\"}"
                .formatted(building, lift, LocalDateTime.now().minusHours(2).withNano(0).format(ISO)), 201);
        postJson("/api/lifts/status/", staff, "{\"building\":%d,\"asset\":%d,\"status\":\"out_of_order\"}"
                .formatted(building, lift), 201);

        JsonNode current = getJson("/api/lifts/current/?building_id=" + building, admin);
        List<JsonNode> mine = new ArrayList<>();
        current.get("results").forEach(row -> {
            if (row.get("asset").asLong() == lift) {
                mine.add(row);
            }
        });
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst().get("status").asString()).isEqualTo("out_of_order");
    }

    @Test
    void aRecurringWasteScheduleRollsForwardInsteadOfDisappearing() throws Exception {
        String admin = tokenFor(ADMIN);
        long building = buildingId(admin);
        LocalDateTime longAgo = LocalDateTime.now().minusWeeks(30).withNano(0);
        JsonNode schedule = postJson("/api/waste-schedules/", admin, "{\"building\":%d,\"schedule_time\":\"%s\",\"recurring\":\"weekly\"}"
                .formatted(building, longAgo.format(ISO)), 201);
        LocalDateTime next = LocalDateTime.parse(schedule.get("next_occurrence").asString());
        assertThat(next).isAfterOrEqualTo(LocalDateTime.now().minusMinutes(1));
        assertThat(next).isBefore(LocalDateTime.now().plusWeeks(1).plusMinutes(1));
        assertThat(next.getDayOfWeek()).isEqualTo(longAgo.getDayOfWeek());

        JsonNode nextCollection = getJson("/api/waste-schedules/next/?building_id=" + building, admin).get("next_collection");
        assertThat(nextCollection.isNull()).isFalse();
    }

    // ---------------------------------------------------------------- rentals

    @Test
    void rentalWorkflowOnlyLetsTheListerDecideAndContractsFollowApproval() throws Exception {
        String committee = tokenFor(COMMITTEE);
        String tenant = tokenFor(TENANT);
        long building = buildingId(committee);
        long listing = postJson("/api/listings/", committee, "{\"building\":%d,\"title\":\"%s\",\"description\":\"nice\",\"rent\":\"50000\",\"available_from\":\"%s\"}"
                .formatted(building, unique("Flat"), LocalDate.now().plusMonths(1)), 201).get("id").asLong();

        postJson("/api/rental-requests/", committee, "{\"listing\":%d}".formatted(listing), 400); // own listing
        long request = postJson("/api/rental-requests/", tenant, "{\"listing\":%d}".formatted(listing), 201).get("id").asLong();
        postJson("/api/rental-requests/", tenant, "{\"listing\":%d}".formatted(listing), 400); // duplicate pending

        postJson("/api/contracts/", committee, "{\"request\":%d,\"contract_path\":\"contracts/x.pdf\"}".formatted(request), 400);
        patchJson("/api/rental-requests/" + request + "/", tenant, "{\"status\":\"approved\"}", 403);
        patchJson("/api/rental-requests/" + request + "/", committee, "{\"status\":\"approved\"}", 200);
        postJson("/api/contracts/", committee, "{\"request\":%d,\"contract_path\":\"contracts/x.pdf\"}".formatted(request), 201);
        postJson("/api/contracts/", committee, "{\"request\":%d,\"contract_path\":\"contracts/y.pdf\"}".formatted(request), 400);
    }

    // ---------------------------------------------------------------- public endpoints

    @Test
    void priceEstimateAndIntercomWebhookArePublicWithTheirContracts() throws Exception {
        ensureSeeded();
        JsonNode hit = postJson("/api/ml/price-estimate", null, "{\"city\":\"dhaka\"}", 200);
        assertThat(hit.get("estimate").asDouble()).isEqualTo(72000.0);
        assertThat(hit.get("model_version").asString()).isEqualTo("v2026.05");
        JsonNode miss = postJson("/api/ml/price-estimate", null, "{\"city\":\"Rangpur\"}", 202);
        assertThat(miss.get("estimate").isNull()).isTrue();
        postJson("/api/ml/price-estimate", null, "{}", 400);

        String admin = tokenFor(ADMIN);
        long device = getJson("/api/intercom/devices/?building_id=" + buildingId(admin), admin)
                .get("results").get(0).get("id").asLong();
        JsonNode logged = postJson("/api/intercom/webhook", null,
                "{\"device_id\":%d,\"event_type\":\"ring\",\"details\":\"test\"}".formatted(device), 201);
        assertThat(logged.get("id").asLong()).isPositive();
        postJson("/api/intercom/webhook", null, "{\"device_id\":999999,\"event_type\":\"ring\"}", 404);
    }

    @Test
    void overviewIntersectsRequestedBuildingsWithTheCallersOwn() throws Exception {
        String resident = tokenFor(RESIDENT);
        long own = buildingId(resident);
        JsonNode foreignOnly = getJson("/api/analytics/overview?building_ids[]=999999", resident);
        assertThat(foreignOnly.get("per_building")).hasSize(1);
        assertThat(foreignOnly.get("per_building").get(0).get("building_id").asLong()).isEqualTo(own);

        JsonNode all = getJson("/api/analytics/overview", tokenFor(ADMIN));
        assertThat(all.get("per_building").size()).isGreaterThanOrEqualTo(2);
        long summed = 0;
        for (JsonNode row : all.get("per_building")) {
            summed += row.get("invoices").asLong();
        }
        assertThat(all.get("invoices").asLong()).isEqualTo(summed);
    }

    @Test
    void clientMistakesAreFourHundredsNotServerErrors() throws Exception {
        String admin = tokenFor(ADMIN);
        getJson("/api/does-not-exist/", admin, 404);
        getJson("/api/units/?building_id=abc", admin, 400);
        getJson("/api/units/abc/", admin, 400);
        mvc.perform(post("/api/units/").header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private long serviceId(String token, String name) throws Exception {
        for (JsonNode s : getJson("/api/services/", token).get("results")) {
            if (name.equals(s.get("name").asString())) {
                return s.get("id").asLong();
            }
        }
        throw new IllegalStateException("No service " + name);
    }

    private long roomId(String token, long building, String name) throws Exception {
        for (JsonNode r : getJson("/api/chat/rooms/?building_id=" + building, token).get("results")) {
            if (name.equals(r.get("name").asString())) {
                return r.get("id").asLong();
            }
        }
        throw new IllegalStateException("No room " + name);
    }

    private JsonNode uploadDocument(String token, long building, String title, Long parent, String filename)
            throws Exception {
        var request = multipart("/api/documents/")
                .file(new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.4 test".getBytes()))
                .param("title", title)
                .param("building", String.valueOf(building))
                .header("Authorization", "Token " + token);
        if (parent != null) {
            request.param("parent", String.valueOf(parent));
        }
        String body = mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private long meterFor(String token, long building, long unit) throws Exception {
        for (JsonNode m : getJson("/api/utility-meters/?building_id=%d&unit_id=%d".formatted(building, unit), token).get("results")) {
            if ("gas".equals(m.get("type").asString())) {
                return m.get("id").asLong();
            }
        }
        return postJson("/api/utility-meters/", token, "{\"unit\":%d,\"type\":\"gas\",\"meter_number\":\"%s\"}"
                .formatted(unit, unique("GAS")), 201).get("id").asLong();
    }

    private List<Long> generate(String token, long building, long billType, String month) throws Exception {
        JsonNode created = postJson("/api/invoices/generate-monthly/", token,
                "{\"building_id\":%d,\"bill_type_id\":%d,\"billing_month\":\"%s\",\"due_date\":\"%s-10\",\"include_utilities\":true}"
                        .formatted(building, billType, month, month), 201);
        List<Long> ids = new ArrayList<>();
        created.get("created_invoices").forEach(id -> ids.add(id.asLong()));
        return ids;
    }

    private JsonNode invoiceFor(String token, List<Long> invoiceIds, long residentId) throws Exception {
        for (long id : invoiceIds) {
            JsonNode invoice = getJson("/api/invoices/" + id + "/", token);
            if (invoice.get("resident").asLong() == residentId) {
                return invoice;
            }
        }
        throw new IllegalStateException("No invoice for resident " + residentId);
    }

    /** A far-future year no previous run has billed: 2100-9899, varied by the clock. */
    private static int freshYear() {
        return 2100 + (int) ((System.currentTimeMillis() / 1000) % 7800);
    }

    private static int utilityItems(JsonNode invoice, long billId) {
        int count = 0;
        for (JsonNode item : invoice.get("items")) {
            if (!item.get("utility_bill_id").isNull() && item.get("utility_bill_id").asLong() == billId) {
                count++;
            }
        }
        return count;
    }

    private static int countEvents(JsonNode audit, String type) {
        int count = 0;
        for (JsonNode row : audit) {
            if (type.equals(row.get("event_type").asString())) {
                count++;
            }
        }
        return count;
    }

    private static List<String> names(JsonNode rows) {
        List<String> names = new ArrayList<>();
        rows.forEach(r -> names.add(r.get("name").asString()));
        return names;
    }
}
