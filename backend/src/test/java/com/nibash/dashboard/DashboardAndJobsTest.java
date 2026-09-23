package com.nibash.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.nibash.jobs.InvoiceJobs;
import com.nibash.support.ApiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/**
 * The dashboard's single-call contract (spec §9) with its no-N+1 guarantee, login → dashboard for
 * every seeded role (spec §16.3), and the two daily invoice jobs (spec §10, §15.10).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardAndJobsTest extends ApiTestSupport {

    private static final List<String> ACCOUNTS = List.of(
            "admin1@nibash.bd", "admin2@nibash.bd", "committee1@nibash.bd", "committee2@nibash.bd",
            "resident1@nibash.bd", "resident2@nibash.bd", "guard1@nibash.bd", "guard2@nibash.bd",
            "staff1@nibash.bd", "staff2@nibash.bd");

    private static final List<String> METRICS = List.of("outstanding", "payments_total", "collection_rate",
            "open_tickets", "visitors_today", "occupancy_rate", "occupied_units", "total_units");

    private static final List<String> SECTIONS = List.of("invoices", "expenses", "notices", "appointments",
            "visitors", "tickets", "services", "vendors", "reviews", "resources", "bookings", "polls", "documents",
            "emergencies", "staff", "attendance", "directory", "intercom_logs", "chat_rooms", "messages", "listings",
            "gate_logs", "lifts", "waste", "units", "assets", "asset_maintenance", "parking_slots", "vehicles",
            "parking_layout", "notifications", "emergency_contacts");

    /**
     * Upper bound on SQL statements for one summary: ~5 metric aggregates, one query per section,
     * a handful of batched collection loads, plus auth and tenancy. An N+1 anywhere would push a
     * seeded building far past it.
     */
    private static final long STATEMENT_BUDGET = 75;

    @Autowired EntityManagerFactory emf;
    @Autowired InvoiceJobs jobs;

    @MockitoBean JavaMailSender mail;

    // ---------------------------------------------------------------- dashboard

    @Test
    void everySeededRoleLogsInAndHydratesTheFullDashboard() throws Exception {
        for (String email : ACCOUNTS) {
            JsonNode summary = getJson("/api/dashboard/summary/", tokenFor(email));
            assertThat(summary.get("building").isNull()).as(email + " has a building").isFalse();
            assertThat(summary.get("me").get("email").asString()).isEqualTo(email);
            for (String metric : METRICS) {
                assertThat(summary.get("metrics").has(metric)).as(email + " metric " + metric).isTrue();
            }
            for (String section : SECTIONS) {
                assertThat(summary.get("sections").has(section)).as(email + " section " + section).isTrue();
            }
            assertThat(summary.get("sections").size()).isEqualTo(SECTIONS.size());
        }
    }

    @Test
    void summaryMatchesItsOwnSectionsAndPrivacyRules() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        JsonNode summary = getJson("/api/dashboard/summary/", resident);
        JsonNode metrics = summary.get("metrics");
        JsonNode sections = summary.get("sections");

        assertThat(metrics.get("total_units").asLong()).isEqualTo(sections.get("units").size());
        assertThat(summary.get("current_resident_id").asLong()).isEqualTo(residentIdOf(resident));
        // The directory's opt-in gating applies here too: resident2 opted out, so no email/phone.
        for (JsonNode entry : sections.get("directory")) {
            if (!entry.get("opt_in").asBoolean()) {
                assertThat(entry.get("email").isNull()).isTrue();
                assertThat(entry.get("phone").isNull()).isTrue();
            }
        }
        // A resident never sees the private committee room in the dashboard either.
        for (JsonNode room : sections.get("chat_rooms")) {
            assertThat(room.get("name").asString()).isNotEqualTo("Committee");
        }
        // Messages come oldest → newest.
        JsonNode messages = sections.get("messages");
        for (int i = 1; i < messages.size(); i++) {
            assertThat(messages.get(i).get("sent_at").asString())
                    .isGreaterThanOrEqualTo(messages.get(i - 1).get("sent_at").asString());
        }
    }

    @Test
    void aForeignBuildingSilentlyFallsBackToTheCallersOwn() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        long own = buildingId(resident);
        JsonNode summary = getJson("/api/dashboard/summary/?building_id=999999", resident);
        assertThat(summary.get("building").get("id").asLong()).isEqualTo(own);
        assertThat(summary.get("buildings")).hasSize(1);
    }

    @Test
    void summaryIssuesABoundedNumberOfStatementsThatDoesNotGrowWithTheData() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = buildingId(admin);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        long before = statementsFor(stats, admin, building);
        assertThat(before).as("statements for one summary").isLessThanOrEqualTo(STATEMENT_BUDGET);

        // 'units' and 'parking_slots' return every row, so they are where an N+1 would show.
        for (int i = 0; i < 4; i++) {
            postJson("/api/units/", admin, "{\"building\":%d,\"unit_number\":\"%s\",\"floor\":9,\"type\":\"2BHK\",\"status\":\"available\"}"
                    .formatted(building, unique("U")), 201);
            postJson("/api/parking/slots/", admin, "{\"building\":%d,\"slot_number\":\"%s\"}"
                    .formatted(building, unique("S")), 201);
        }
        long after = statementsFor(stats, admin, building);
        assertThat(after).as("statements after adding 8 rows").isEqualTo(before);
    }

    private long statementsFor(Statistics stats, String token, long building) throws Exception {
        stats.clear();
        getJson("/api/dashboard/summary/?building_id=" + building, token);
        return stats.getPrepareStatementCount();
    }

    // ---------------------------------------------------------------- jobs

    @Test
    void overdueSweepAndRemindersPickTheRightInvoicesAndSurviveABadAddress() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long ayesha = residentIdOf(tokenFor("resident1@nibash.bd"));
        long rafiq = residentIdOf(tokenFor("resident2@nibash.bd"));
        LocalDate today = LocalDate.now();

        String pastDue = invoice(admin, ayesha, today.minusDays(1), "pending");
        String dueToday = invoice(admin, rafiq, today, "pending");
        String dueLater = invoice(admin, rafiq, today.plusDays(10), "pending");
        String paid = invoice(admin, rafiq, today.minusDays(1), "paid");

        jobs.markOverdue();
        assertThat(statusOf(admin, pastDue)).isEqualTo("overdue");
        assertThat(statusOf(admin, dueToday)).isEqualTo("pending");
        assertThat(statusOf(admin, paid)).isEqualTo("paid");

        // Ayesha's mailbox rejects everything; the batch must carry on past her.
        List<String> delivered = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            if (message.getTo()[0].equals("resident1@nibash.bd")) {
                throw new MailSendException("550 mailbox unavailable");
            }
            delivered.add(message.getSubject());
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));

        int sent = jobs.sendReminders();

        assertThat(sent).isPositive();
        assertThat(delivered).anyMatch(subject -> subject.equals("Reminder: Invoice %s due %s".formatted(dueToday, today)));
        assertThat(delivered).noneMatch(subject -> subject.contains(dueLater));
        assertThat(delivered).noneMatch(subject -> subject.contains(paid));
        assertThat(delivered).noneMatch(subject -> subject.contains(pastDue)); // rejected, not delivered
    }

    @Test
    void remindButtonNotifiesTheResidentInApp() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        String resident = tokenFor("resident1@nibash.bd");
        long ayesha = residentIdOf(resident);
        String number = invoice(admin, ayesha, LocalDate.now().plusDays(2), "pending");
        long id = findInvoice(admin, number).get("id").asLong();

        JsonNode reply = postJson("/api/invoices/" + id + "/remind/", admin, null, 200);
        assertThat(reply.get("detail").asString()).isEqualTo("Reminder queued for invoice " + number);

        JsonNode feed = getJson("/api/notifications/?building_id=" + buildingId(resident), resident);
        assertThat(feed.get("results").get(0).get("type").asString()).isEqualTo("invoice");
        assertThat(feed.get("results").get(0).get("message").asString()).contains(number);
    }

    // ---------------------------------------------------------------- helpers

    private String invoice(String token, long resident, LocalDate due, String status) throws Exception {
        String number = unique("REM");
        postJson("/api/invoices/", token, "{\"resident\":%d,\"invoice_number\":\"%s\",\"due_date\":\"%s\",\"status\":\"%s\",\"amount\":\"1500.00\"}"
                .formatted(resident, number, due, status), 201);
        return number;
    }

    private JsonNode findInvoice(String token, String number) throws Exception {
        for (int page = 1; page < 50; page++) {
            JsonNode rows = getJson("/api/invoices/?page=" + page, token);
            for (JsonNode row : rows.get("results")) {
                if (number.equals(row.get("invoice_number").asString())) {
                    return row;
                }
            }
            if (rows.get("next").isNull()) {
                break;
            }
        }
        throw new IllegalStateException("No invoice " + number);
    }

    private String statusOf(String token, String number) throws Exception {
        return findInvoice(token, number).get("status").asString();
    }
}
