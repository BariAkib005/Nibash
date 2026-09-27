package com.nibash.jobs;

import com.nibash.common.NamedThreadFactory;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbound channels (spec §10): email is real SMTP; SMS and push are stubs that report success —
 * the integration points for an SMS gateway and FCM.
 *
 * <p>Every channel <b>fails silently</b>: a bad address or a down mail server is logged and
 * reported as {@code false}, never thrown, so one broken recipient cannot stop a batch.
 *
 * <p><b>Mail runs on its own thread pool</b> ({@code mail-1…n}, {@code nibash.mail.threads}). An SMTP
 * round trip can take seconds — up to the 5 s timeouts when the server is unreachable — so
 * {@link #emailAll} sends a whole batch concurrently instead of one after another, and
 * {@link #emailLater} hands a single message to the pool once the caller's transaction commits, so
 * an HTTP request never waits on SMTP and a rolled-back change never sends mail.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final ExecutorService mailPool;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${nibash.mail.from:no-reply@nibash.bd}") String from,
                               @Value("${nibash.mail.threads:4}") int threads) {
        this.mailSender = mailSender;
        this.from = from;
        this.mailPool = Executors.newFixedThreadPool(Math.max(1, threads), new NamedThreadFactory("mail"));
    }

    /** One message of a batch. */
    public record Email(String to, String subject, String body) {
    }

    /**
     * Sends every message concurrently on the mail pool and waits for all of them.
     *
     * @return how many were handed to the SMTP server
     */
    public int emailAll(List<Email> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        List<Callable<Boolean>> tasks = batch.stream()
                .map(mail -> (Callable<Boolean>) () -> email(mail.to(), mail.subject(), mail.body()))
                .toList();
        int sent = 0;
        try {
            for (Future<Boolean> result : mailPool.invokeAll(tasks)) {
                try {
                    if (Boolean.TRUE.equals(result.get())) {
                        sent++;
                    }
                } catch (ExecutionException e) {
                    log.warn("A batch email failed: {}", e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Email batch interrupted after {} of {} sent", sent, batch.size());
        }
        return sent;
    }

    /**
     * Queues one message on the mail pool without waiting for it — after the current transaction
     * commits when there is one, straight away otherwise.
     */
    public void emailLater(String to, String subject, String body) {
        Runnable enqueue = () -> {
            try {
                mailPool.execute(() -> email(to, subject, body));
            } catch (RejectedExecutionException e) {
                log.warn("Mail pool is shut down; dropped email to {}", to);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue.run();
                }
            });
        } else {
            enqueue.run();
        }
    }

    /** Lets queued mail finish (briefly) on shutdown rather than cutting it off mid-send. */
    @PreDestroy
    void shutdown() throws InterruptedException {
        mailPool.shutdown();
        if (!mailPool.awaitTermination(10, TimeUnit.SECONDS)) {
            mailPool.shutdownNow();
        }
    }

    /** @return true when the message was handed to the SMTP server. */
    public boolean email(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.debug("Mail is not configured; skipping email to {}", to);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to.trim());
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (RuntimeException e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }

    /** Stub — the SMS gateway integration point. */
    public boolean sms(String phone, String message) {
        log.debug("SMS stub → {}: {}", phone, message);
        return phone != null && !phone.isBlank();
    }

    /** Stub — the FCM integration point. */
    public boolean push(String token, String title, String body, Map<String, String> data) {
        log.debug("Push stub → {}: {} ({})", token, title, data);
        return token != null && !token.isBlank();
    }
}
