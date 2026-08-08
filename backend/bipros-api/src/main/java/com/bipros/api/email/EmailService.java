package com.bipros.api.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Report email sender (HTML body + optional PDF attachment). Coexists with the agent-alert
 * {@code EmailChannel} deliberately — alerts have no attachment path. Both gate on the same
 * single SMTP config: Spring only creates a {@link JavaMailSender} when {@code spring.mail.host}
 * ({@code SMTP_HOST}) is set; without it every send is a logged PREVIEW.
 *
 * <p>Port note (2026-08-05): the branch hard-injected {@link JavaMailSender}, which failed boot
 * whenever SMTP was unset — switched to {@link ObjectProvider} (same pattern as EmailChannel).
 * From address: {@code bipros.dpr.report.from-address} if set, else {@code spring.mail.username}
 * (gmail/outlook reject a mismatched sender), else a local fallback.
 */
@Service
@Slf4j
public class EmailService {
    public enum SendResult { SENT, PREVIEW, FAILED }

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String fromAddress;
    private final String mailUsername;

    public EmailService(ObjectProvider<JavaMailSender> senderProvider,
                        @Value("${bipros.dpr.report.from-address:}") String fromAddress,
                        @Value("${spring.mail.username:}") String mailUsername) {
        this.senderProvider = senderProvider;
        this.fromAddress = fromAddress;
        this.mailUsername = mailUsername;
    }

    private String resolveFrom() {
        if (fromAddress != null && !fromAddress.isBlank()) {
            return fromAddress;
        }
        return (mailUsername == null || mailUsername.isBlank()) ? "no-reply@bipros.local" : mailUsername;
    }

    public SendResult send(EmailMessage msg) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || msg.to() == null || msg.to().isEmpty()) {
            log.info("[EmailService] PREVIEW (no SMTP configured or no recipients) subject='{}' to={} bytes(html)={}",
                msg.subject(), msg.to(), msg.html() == null ? 0 : msg.html().length());
            return SendResult.PREVIEW;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, msg.attachment() != null, "UTF-8");
            h.setFrom(resolveFrom());
            h.setTo(msg.to().toArray(String[]::new));
            h.setSubject(msg.subject());
            h.setText(msg.html(), true);
            if (msg.attachment() != null && msg.attachmentName() != null) {
                h.addAttachment(msg.attachmentName(), new ByteArrayResource(msg.attachment()));
            }
            sender.send(mime);
            return SendResult.SENT;
        } catch (Exception e) {
            log.warn("[EmailService] send failed: {}", e.getMessage(), e);
            return SendResult.FAILED;
        }
    }
}
