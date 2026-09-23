package com.nibash.jobs;

import com.nibash.finance.Invoice;
import com.nibash.finance.InvoiceRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two daily invoice jobs, both on Asia/Dhaka time.
 *
 * <ul>
 *   <li><b>Overdue sweep</b>, 00:05 — pending invoices past their due date become {@code overdue}.
 *       The spec leaves this unautomated (§15.10) and sanctions adding it.</li>
 *   <li><b>Reminders</b>, 08:00 (spec §10) — every pending or overdue invoice due by tomorrow gets
 *       an email. Rows without an address are skipped and a failure for one recipient never stops
 *       the rest.</li>
 * </ul>
 * Both are plain public methods too, so tests and operators can run them on demand.
 */
@Component
public class InvoiceJobs {

    private static final Logger log = LoggerFactory.getLogger(InvoiceJobs.class);

    private final InvoiceRepository invoices;
    private final NotificationService notifications;
    private final ZoneId zone;

    public InvoiceJobs(InvoiceRepository invoices, NotificationService notifications,
                       @Value("${nibash.timezone}") String timezone) {
        this.invoices = invoices;
        this.notifications = notifications;
        this.zone = ZoneId.of(timezone);
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${nibash.timezone}")
    @Transactional
    public int markOverdue() {
        int flipped = invoices.markOverdue(LocalDate.now(zone));
        if (flipped > 0) {
            log.info("Overdue sweep: {} invoice(s) marked overdue", flipped);
        }
        return flipped;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${nibash.timezone}")
    @Transactional(readOnly = true)
    public int sendReminders() {
        List<Invoice> due = invoices.findDueForReminder(LocalDate.now(zone).plusDays(1));
        int sent = 0;
        for (Invoice invoice : due) {
            String email = invoice.getResident().getUser().getEmail();
            if (email == null || email.isBlank()) {
                continue;
            }
            try {
                if (notifications.email(email, subject(invoice), body(invoice))) {
                    sent++;
                }
            } catch (RuntimeException e) {
                // NotificationService already swallows mail errors; this guards anything unexpected
                log.warn("Reminder for invoice {} failed: {}", invoice.getInvoiceNumber(), e.getMessage());
            }
        }
        log.info("Invoice reminders: {} sent of {} due", sent, due.size());
        return sent;
    }

    /** Spec §10 wording, verbatim. */
    public static String subject(Invoice invoice) {
        return "Reminder: Invoice %s due %s".formatted(invoice.getInvoiceNumber(), invoice.getDueDate());
    }

    public static String body(Invoice invoice) {
        return "Dear resident, your invoice %s of amount %s is due on %s."
                .formatted(invoice.getInvoiceNumber(), invoice.getAmount(), invoice.getDueDate());
    }
}
