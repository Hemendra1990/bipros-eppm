package com.bipros.ai.agent.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Email channel. A single inline-HTML message with a gold header and the finding's five sections
 * (What happened / Why / Business impact / Recommended action / Stakeholders) plus a deep-link
 * button. Money already arrives pre-formatted in the finding fields and is rendered as-is (currency
 * relabel-only — never converted).
 *
 * <p>The whole channel is gated on an SMTP host being configured under
 * {@code bipros.agent.notify.email.host} AND a {@link JavaMailSender} bean being present (Spring
 * Boot only auto-configures one when {@code spring.mail.host} is set). When either is missing the
 * channel disables itself (logged once) and every send is a no-op. Never throws.
 */
@Slf4j
@Component
public class EmailChannel implements NotificationChannel {

    public static final String KEY = "email";

    private static final String GOLD = "#C9A227";
    private static final String GOLD_DARK = "#8A6D1B";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AgentNotifyProperties props;
    private final AtomicBoolean warnedDisabled = new AtomicBoolean(false);

    public EmailChannel(ObjectProvider<JavaMailSender> mailSenderProvider, AgentNotifyProperties props) {
        this.mailSenderProvider = mailSenderProvider;
        this.props = props;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public boolean isEnabled() {
        String host = props.getEmail().getHost();
        boolean hostSet = host != null && !host.isBlank();
        boolean senderAvailable = mailSenderProvider.getIfAvailable() != null;
        if (!hostSet || !senderAvailable) {
            if (warnedDisabled.compareAndSet(false, true)) {
                log.info("EmailChannel disabled (host configured={}, JavaMailSender present={}); "
                        + "agent emails will be skipped until both are set.", hostSet, senderAvailable);
            }
            return false;
        }
        return true;
    }

    @Override
    public void send(ResolvedNotification n) {
        if (!isEnabled() || isBlank(n.email())) {
            return;
        }
        try {
            JavaMailSender sender = mailSenderProvider.getObject();
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(props.getEmail().getFrom());
            helper.setTo(n.email());
            helper.setSubject(severityTag(n.severity()) + safe(n.title()));
            helper.setText(buildHtml(n), true);
            sender.send(msg);
        } catch (Exception ex) {
            log.warn("EmailChannel send failed for finding {} to {}: {}",
                    n.findingId(), n.email(), ex.getMessage());
        }
    }

    /** One rolled-up digest email listing several deferred findings. Best-effort; never throws. */
    public void sendDigest(String toEmail, String recipientName, List<ResolvedNotification> items) {
        if (!isEnabled() || isBlank(toEmail) || items == null || items.isEmpty()) {
            return;
        }
        try {
            JavaMailSender sender = mailSenderProvider.getObject();
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(props.getEmail().getFrom());
            helper.setTo(toEmail);
            helper.setSubject("Daily AI digest (" + items.size() + " finding" + (items.size() == 1 ? "" : "s") + ")");
            helper.setText(buildDigestHtml(recipientName, items), true);
            sender.send(msg);
        } catch (Exception ex) {
            log.warn("EmailChannel digest send failed to {}: {}", toEmail, ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- html

    private String buildHtml(ResolvedNotification n) {
        String url = absoluteLink(n.deepLink());
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:640px;margin:0 auto;"
                + "border:1px solid #e5e5e5;border-radius:8px;overflow:hidden\">");
        sb.append(header(safe(n.title()), n.severity() == null ? "" : n.severity().name()));
        sb.append("<div style=\"padding:20px 24px;color:#222;font-size:14px;line-height:1.55\">");
        section(sb, "What happened", n.whatHappened());
        section(sb, "Why it happened", n.whyItHappened());
        section(sb, "Business impact", n.businessImpact());
        section(sb, "Recommended action", n.recommendedAction());
        if (n.stakeholderLabels() != null && !n.stakeholderLabels().isEmpty()) {
            section(sb, "Stakeholders", String.join(", ", n.stakeholderLabels()));
        }
        if (n.confidenceBasis() != null && !n.confidenceBasis().isBlank()) {
            sb.append("<p style=\"color:#888;font-size:12px;margin:18px 0 0\">Basis: ")
                    .append(safe(n.confidenceBasis())).append("</p>");
        }
        sb.append(button(url, "Open in Bipros"));
        sb.append("</div></div>");
        return sb.toString();
    }

    private String buildDigestHtml(String recipientName, List<ResolvedNotification> items) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:640px;margin:0 auto;"
                + "border:1px solid #e5e5e5;border-radius:8px;overflow:hidden\">");
        sb.append(header("Daily AI digest", items.size() + " findings"));
        sb.append("<div style=\"padding:20px 24px;color:#222;font-size:14px;line-height:1.55\">");
        if (!isBlank(recipientName)) {
            sb.append("<p style=\"margin:0 0 12px\">Hello ").append(safe(recipientName)).append(",</p>");
        }
        sb.append("<p style=\"margin:0 0 16px\">The following items are awaiting your attention:</p>");
        sb.append("<ul style=\"padding-left:18px;margin:0\">");
        for (ResolvedNotification it : items) {
            sb.append("<li style=\"margin:0 0 12px\">")
                    .append("<a href=\"").append(absoluteLink(it.deepLink()))
                    .append("\" style=\"color:").append(GOLD_DARK).append(";font-weight:bold;text-decoration:none\">")
                    .append(safe(it.title())).append("</a>");
            if (!isBlank(it.businessImpact())) {
                sb.append("<div style=\"color:#555;font-size:13px;margin-top:2px\">")
                        .append(safe(it.businessImpact())).append("</div>");
            }
            sb.append("</li>");
        }
        sb.append("</ul></div></div>");
        return sb.toString();
    }

    private String header(String title, String tag) {
        return "<div style=\"background:linear-gradient(135deg," + GOLD + "," + GOLD_DARK + ");"
                + "padding:18px 24px;color:#fff\">"
                + "<div style=\"font-size:11px;letter-spacing:1.5px;text-transform:uppercase;opacity:.85\">"
                + "Bipros AI" + (isBlank(tag) ? "" : " &middot; " + safe(tag)) + "</div>"
                + "<div style=\"font-size:18px;font-weight:bold;margin-top:4px\">" + title + "</div></div>";
    }

    private static void section(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("<p style=\"margin:14px 0 2px;font-size:11px;letter-spacing:.5px;text-transform:uppercase;"
                        + "color:").append(GOLD_DARK).append(";font-weight:bold\">").append(label).append("</p>")
                .append("<p style=\"margin:0;color:#333\">").append(safe(value)).append("</p>");
    }

    private String button(String url, String label) {
        return "<div style=\"margin-top:22px\"><a href=\"" + url + "\" style=\"display:inline-block;"
                + "background:" + GOLD_DARK + ";color:#fff;text-decoration:none;padding:10px 20px;"
                + "border-radius:6px;font-weight:bold;font-size:14px\">" + label + "</a></div>";
    }

    private String absoluteLink(String relative) {
        String base = props.getEmail().getAppBaseUrl();
        if (isBlank(relative)) {
            return isBlank(base) ? "#" : base;
        }
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        if (isBlank(base)) {
            return relative;
        }
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String r = relative.startsWith("/") ? relative : "/" + relative;
        return b + r;
    }

    private static String severityTag(com.bipros.ai.agent.core.Severity severity) {
        return severity == null ? "" : "[" + severity.name() + "] ";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Minimal HTML escaping for finding text rendered into the template. */
    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
