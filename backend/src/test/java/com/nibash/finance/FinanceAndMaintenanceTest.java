package com.nibash.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nibash.seed.DemoSeeder;
import com.nibash.staffing.AttendanceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
 * Week 3 definition of done (plan §Week 3): the money rules and the work-order rules.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional} — two of these tests are about what happens when
 * two real transactions race, which a test-managed rollback would hide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinanceAndMaintenanceTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoSeeder seeder;
    @Autowired InvoiceRepository invoices;
    @Autowired PaymentRepository payments;
    @Autowired AttendanceRepository attendance;

    private static boolean seeded = false;

    /** Invoice numbers this test created, torn down afterwards so re-runs start clean. */
    private final java.util.List<String> createdInvoiceNumbers = new java.util.ArrayList<>();

    @BeforeAll
    static void resetFlag() {
        seeded = false;
    }

    @AfterEach
    void cleanUpInvoices() {
        for (String number : createdInvoiceNumbers) {
            invoices.findByInvoiceNumber(number).ifPresent(invoice -> {
                payments.findAll().stream()
                        .filter(p -> p.getInvoice().getId().equals(invoice.getId()))
                        .forEach(payments::delete);
                invoices.delete(invoice);
            });
        }
        createdInvoiceNumbers.clear();
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

    private long gulshanBuildingId(String token) throws Exception {
        return getJson("/api/auth/me/", token).get("building").get("id").asLong();
    }

    /** The resident row for {@code resident1@nibash.bd} in the caller's building. */
    private long firstResidentId(String token, long buildingId) throws Exception {
        JsonNode page = getJson("/api/residents/?building_id=" + buildingId, token);
        return page.get("results").get(0).get("id").asLong();
    }

    private long ensureBillType(String token) throws Exception {
        JsonNode page = getJson("/api/bill-types/", token);
        for (JsonNode row : page.get("results")) {
            if ("Service Charge".equals(row.get("name").asString())) {
                return row.get("id").asLong();
            }
        }
        String created = mvc.perform(post("/api/bill-types/")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"name":"Service Charge","description":"Monthly building charge"}
                                 """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    /** A billing month nobody else has used, so the idempotency assertions mean something. */
    private String uniqueBillingMonth() {
        int year = 2100 + (int) (System.nanoTime() % 500);
        return "%d-07".formatted(year);
    }

    private long createInvoice(String token, long residentId, String amount) throws Exception {
        String number = "TEST-" + System.nanoTime();
        createdInvoiceNumbers.add(number);

        String created = mvc.perform(post("/api/invoices/")
                        .header("Authorization", "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"invoice_number":"%s","resident":%d,"due_date":"2026-12-31",
                                  "items":[{"description":"Service charge","quantity":1,"unit_price":%s,"total_amount":%s}]}
                                 """.formatted(number, residentId, amount, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    // ---------------------------------------------------------------- generate-monthly

    @Test
    void generateMonthlyIsIdempotent() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long billType = ensureBillType(admin);
        String month = uniqueBillingMonth();

        String payload = """
                         {"building_id":%d,"bill_type_id":%d,"billing_month":"%s","due_date":"2026-12-31"}
                         """.formatted(building, billType, month);

        String first = mvc.perform(post("/api/invoices/generate-monthly/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstIds = json.readTree(first).get("created_invoices");
        assertThat(firstIds).isNotEmpty();
        firstIds.forEach(id -> invoices.findById(id.asLong())
                .ifPresent(invoice -> createdInvoiceNumbers.add(invoice.getInvoiceNumber())));

        // The whole point: pressing the button twice must not bill anyone twice.
        String second = mvc.perform(post("/api/invoices/generate-monthly/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second).get("created_invoices")).isEmpty();
    }

    @Test
    void generateMonthlyRejectsAnInaccessibleBuilding() throws Exception {
        String resident = tokenFor("resident1@nibash.bd");
        String admin2 = tokenFor("admin2@nibash.bd");
        long otherBuilding = gulshanBuildingId(admin2);        // Banani
        long billType = ensureBillType(tokenFor("admin1@nibash.bd"));

        mvc.perform(post("/api/invoices/generate-monthly/")
                        .header("Authorization", "Token " + resident)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"building_id":%d,"bill_type_id":%d,"billing_month":"2099-01","due_date":"2099-02-01"}
                                 """.formatted(otherBuilding, billType)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- checkout

    @Test
    void checkoutOfAPaidInvoiceIsRejected() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long resident = firstResidentId(admin, building);
        long invoiceId = createInvoice(admin, resident, "1500.00");

        mvc.perform(post("/api/payments/checkout/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoice_id\":%d}".formatted(invoiceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkout_status").value("paid"))
                .andExpect(jsonPath("$.transaction_id").isNotEmpty());

        mvc.perform(post("/api/payments/checkout/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoice_id\":%d}".formatted(invoiceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invoice is already paid."));
    }

    @Test
    void concurrentCheckoutWritesExactlyOnePayment() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long resident = firstResidentId(admin, building);
        long invoiceId = createInvoice(admin, resident, "2750.00");

        int threads = 2;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        Callable<Void> attempt = () -> {
            startTogether.await();
            int status = mvc.perform(post("/api/payments/checkout/")
                            .header("Authorization", "Token " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"invoice_id\":%d}".formatted(invoiceId)))
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
            List<Future<Void>> results = pool.invokeAll(List.of(attempt, attempt));
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // The pessimistic lock is what makes this deterministic rather than a coin flip.
        assertThat(created.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(payments.countByInvoiceId(invoiceId)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- expenses

    @Test
    void expenseRejectsFutureDateAndNonPositiveAmount() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        String future = LocalDate.now().plusDays(3).toString();

        mvc.perform(post("/api/expenses/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"building":%d,"category":"Lift AMC","amount":"5000.00","date":"%s"}
                                 """.formatted(building, future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Expense date cannot be in the future."));

        mvc.perform(post("/api/expenses/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"building":%d,"category":"Lift AMC","amount":"-1.00","date":"2026-01-05"}
                                 """.formatted(building)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Amount must be positive."));
    }

    @Test
    void monthlyExpenseReportGroupsByMonthAndCategory() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);

        mvc.perform(post("/api/expenses/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"building":%d,"category":"Generator Fuel","amount":"4200.00","date":"2026-03-11"}
                                 """.formatted(building)))
                .andExpect(status().isCreated());

        JsonNode report = getJson("/api/expenses/reports/monthly/?building_id=" + building, admin);
        assertThat(report.get("results")).isNotEmpty();
        assertThat(report.get("results").get(0).has("month")).isTrue();
        assertThat(report.get("results").get(0).has("category")).isTrue();
    }

    // ---------------------------------------------------------------- attendance

    @Test
    void doubleCheckinReturnsTheSameOpenShift() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long staffId = getJson("/api/staff/?building_id=" + building, admin)
                .get("results").get(0).get("id").asLong();

        // Leave no shift open from an earlier run, so the first call below is genuinely the first.
        attendance.findFirstByStaffIdAndCheckoutTimeIsNullOrderByCheckinTimeDesc(staffId)
                .ifPresent(open -> {
                    open.setCheckoutTime(java.time.LocalDateTime.now());
                    attendance.save(open);
                });

        String first = mvc.perform(post("/api/attendance/checkin/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staff_id\":%d}".formatted(staffId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstId = json.readTree(first).get("id").asLong();

        // A guard tapping twice must not produce a second shift.
        String second = mvc.perform(post("/api/attendance/checkin/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staff_id\":%d}".formatted(staffId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second).get("id").asLong()).isEqualTo(firstId);

        mvc.perform(post("/api/attendance/checkout/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staff_id\":%d}".formatted(staffId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkout_time").isNotEmpty());

        mvc.perform(post("/api/attendance/checkout/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staff_id\":%d}".formatted(staffId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No open attendance record"));
    }

    // ---------------------------------------------------------------- tickets

    @Test
    void ticketAutoAssignmentPicksACategoryMatchingStaffMember() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long resident = firstResidentId(admin, building);

        // The Gulshan seed has a staff member whose role is exactly "Maintenance" (Kamal Sheikh).
        String created = mvc.perform(post("/api/tickets/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"resident":%d,"category":"Maintenance","description":"Kitchen tap is leaking"}
                                 """.formatted(resident)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("open"))
                .andReturn().getResponse().getContentAsString();

        JsonNode ticket = json.readTree(created);
        assertThat(ticket.get("assigned_to").isNull()).isFalse();
        assertThat(ticket.get("assigned_to_name").asString()).isEqualTo("Kamal Sheikh");
    }

    @Test
    void ticketStatusStampsAndClearsClosedAt() throws Exception {
        String admin = tokenFor("admin1@nibash.bd");
        long building = gulshanBuildingId(admin);
        long resident = firstResidentId(admin, building);

        String created = mvc.perform(post("/api/tickets/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"resident":%d,"category":"Cleaning","description":"Corridor needs mopping"}
                                 """.formatted(resident)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ticketId = json.readTree(created).get("id").asLong();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/tickets/" + ticketId + "/status/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed_at").isNotEmpty());

        // Reopening must not leave a stale closed_at behind.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/tickets/" + ticketId + "/status/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed_at").doesNotExist());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/tickets/" + ticketId + "/status/")
                        .header("Authorization", "Token " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"banana\"}"))
                .andExpect(status().isBadRequest());
    }
}
