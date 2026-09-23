package com.nibash.jobs;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Outbound channels (spec §10): email is real SMTP; SMS and push are stubs that report success —
 * the integration points for an SMS gateway and FCM.
 *
 * <p>Every channel <b>fails silently</b>: a bad address or a down mail server is logged and
 * reported as {@code false}, never thrown, so one broken recipient cannot stop a batch.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${nibash.mail.from:no-reply@nibash.bd}") String from) {
        this.mailSender = mailSender;
        this.from = from;
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
