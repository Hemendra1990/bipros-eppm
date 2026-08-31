package com.bipros.api.notification;

import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.application.service.ProjectTeamService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.application.dto.IdleStockRow;
import com.bipros.resource.application.service.MaterialIdleStockService;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Idle-material alert (owner request 2026-08-12). When an activity nears completion with material
 * still outstanding against a custodian, tell the custodian and their reporting manager how much
 * of it the remaining work no longer needs, so it can be consumed, returned for re-issue, or
 * written off as scrap.
 *
 * <p>The engine ({@link MaterialIdleStockService}) decides what counts as excess; this service
 * only handles delivery and pacing. One email per custodian listing every material they hold —
 * never one mail per row. Pacing has three guards: nothing goes out twice on the same day, each
 * open item is reminded at most {@code material_idle_max_reminders} times, and an item whose
 * excess falls back under tolerance resolves itself on the next evaluation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialIdleAlertService {

    private static final String POOL = "POOL";

    private final MaterialIdleStockService idleStockService;
    private final MaterialIdleAlertRepository alertRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamService projectTeamService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AgentMailLogService mailLogService;
    private final DprAlertConfig alertConfig;
    private final DprReportConfig reportConfig;

    @Transactional
    public void runForProject(UUID projectId, LocalDate asOf) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        List<IdleStockRow> rows = idleStockService.evaluate(projectId, asOf, alertConfig.idleThresholds());
        List<IdleStockRow> alerting = rows.stream().filter(IdleStockRow::alerting).toList();

        Set<String> stillAlerting = new LinkedHashSet<>();
        Map<UUID, List<IdleStockRow>> byCustodian = new LinkedHashMap<>();
        for (IdleStockRow r : alerting) {
            stillAlerting.add(key(r));
            byCustodian.computeIfAbsent(r.custodianUserId(), k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<UUID, List<IdleStockRow>> e : byCustodian.entrySet()) {
            try {
                notifyCustodian(project, e.getKey(), e.getValue(), asOf);
            } catch (Exception ex) {
                log.warn("[MaterialIdleAlert] custodian={} failed: {}", e.getKey(), ex.getMessage(), ex);
            }
        }

        // Anything previously open that no longer breaches is settled — consumed, returned or scrapped.
        for (MaterialIdleAlert open : alertRepository.findByProjectIdAndResolvedAtIsNull(projectId)) {
            if (!stillAlerting.contains(key(open))) {
                open.setResolvedAt(Instant.now());
                alertRepository.save(open);
            }
        }
    }

    // ---------------------------------------------------------------- delivery

    private void notifyCustodian(Project project, UUID custodianUserId,
                                 List<IdleStockRow> rows, LocalDate asOf) {
        LocalDate today = LocalDate.now(reportConfig.zone());
        int maxReminders = alertConfig.materialIdleMaxReminders();

        List<IdleStockRow> sendable = new ArrayList<>();
        List<MaterialIdleAlert> alerts = new ArrayList<>();
        for (IdleStockRow r : rows) {
            MaterialIdleAlert alert = alertRepository
                .findByProjectIdAndCustodianUserIdAndMaterialKeyAndBucketKey(
                    project.getId(), custodianUserId, r.materialKey(), bucketKey(r))
                .orElse(null);
            if (alert != null) {
                if (alert.getReminderCount() >= maxReminders) {
                    continue; // capped — it lives in the weekly digest from here
                }
                if (alert.getLastSentAt() != null
                    && LocalDate.ofInstant(alert.getLastSentAt(), reportConfig.zone()).equals(today)) {
                    continue; // already told them today
                }
            }
            sendable.add(r);
            alerts.add(alert);
        }
        if (sendable.isEmpty()) {
            return;
        }

        User custodian = userRepository.findById(custodianUserId).orElse(null);
        if (custodian == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(custodianUserId);
        projectTeamService.getImmediateReporter(project.getId(), custodianUserId)
            .or(() -> projectTeamService.resolveCmFor(project.getId(), custodianUserId))
            .or(() -> projectTeamService.resolvePmFor(project.getId(), custodianUserId))
            .ifPresent(recipients::add);

        IdleStockRow worst = sendable.get(0);
        String subject = "Material still outstanding — %s · %s %s %s"
            .formatted(project.getName(), qty(worst.excess()), safeUnit(worst.unit()), worst.materialName());
        String link = "/projects/" + project.getId() + "/material-issues";
        String html = html(project, displayName(custodian), sendable, alertConfig.appBaseUrl() + link);
        String body = "%d material item(s) outstanding against %s."
            .formatted(sendable.size(), displayName(custodian));

        for (UUID userId : recipients) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            try {
                notificationService.create(userId, DprNotificationType.MATERIAL_IDLE_STOCK,
                    "Material still outstanding", body, link, project.getId(), null);
                mailLogService.log(row(project.getId(), AgentMailLog.CH_IN_APP, userId,
                    displayName(user), null, "Material still outstanding", null, "SENT", null));
            } catch (Exception ex) {
                log.warn("[MaterialIdleAlert] in-app failed for {}: {}", userId, ex.getMessage());
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                mailLogService.log(row(project.getId(), AgentMailLog.CH_EMAIL, userId,
                    displayName(user), null, subject, null, AgentMailLog.STATUS_SKIPPED,
                    "user has no email"));
                continue;
            }
            EmailService.SendResult result = emailService.send(
                new EmailMessage(List.of(user.getEmail()), subject, html, null, null));
            mailLogService.log(row(project.getId(), AgentMailLog.CH_EMAIL, userId, displayName(user),
                user.getEmail(), subject, html, result.name(), null));
        }

        Instant now = Instant.now();
        for (int i = 0; i < sendable.size(); i++) {
            IdleStockRow r = sendable.get(i);
            MaterialIdleAlert alert = alerts.get(i);
            if (alert == null) {
                alert = new MaterialIdleAlert();
                alert.setProjectId(project.getId());
                alert.setCustodianUserId(custodianUserId);
                alert.setMaterialKey(r.materialKey());
                alert.setBucketKey(bucketKey(r));
                alert.setActivityId(r.activityId());
                alert.setFirstExcess(r.excess());
                alert.setFirstSentAt(now);
            }
            alert.setLastExcess(r.excess());
            alert.setLastSentAt(now);
            alert.setReminderCount(alert.getReminderCount() + 1);
            alert.setResolvedAt(null);
            alertRepository.save(alert);
        }
        log.info("[MaterialIdleAlert] project={} custodian={} rows={} recipients={}",
            project.getId(), custodianUserId, sendable.size(), recipients.size());
    }

    // ---------------------------------------------------------------- content

    private String html(Project project, String custodianName, List<IdleStockRow> rows, String href) {
        String currency = project.getBudgetCurrency() != null ? project.getBudgetCurrency() : "";
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:680px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden\">")
         .append("<div style=\"background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold\">Material still outstanding — ")
         .append(HtmlUtils.htmlEscape(project.getName() != null ? project.getName() : "Project"))
         .append("</div><div style=\"padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6\">")
         .append("<p>Hello ").append(HtmlUtils.htmlEscape(custodianName)).append(",</p>")
         .append("<p>Work on the front(s) below is nearly done, but store material issued to you is still unaccounted for. ")
         .append("The figures come from your approved DPRs and the store issue register.</p>");

        for (IdleStockRow r : rows) {
            String unit = safeUnit(r.unit());
            b.append("<div style=\"border:1px solid #e5e0d8;border-radius:6px;padding:12px 14px;margin:14px 0\">")
             .append("<div style=\"font-weight:bold;font-size:15px\">")
             .append(HtmlUtils.htmlEscape(r.materialName())).append("</div>");
            if (r.activityName() != null) {
                b.append("<div style=\"color:#6b6b6b;font-size:12px\">")
                 .append(HtmlUtils.htmlEscape(r.activityName()))
                 .append(" — ").append(qty(r.percentComplete())).append("% complete</div>");
            } else {
                b.append("<div style=\"color:#6b6b6b;font-size:12px\">Across your open activities — highest is ")
                 .append(qty(r.percentComplete())).append("% complete</div>");
            }
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;margin-top:8px\">");
            line(b, "Issued from store", qty(r.issuedToDate()) + " " + unit
                + (r.earliestIssueDate() != null ? " (from " + r.earliestIssueDate() + ")" : ""));
            if (r.returnedToDate().signum() > 0) {
                line(b, "Already returned", qty(r.returnedToDate()) + " " + unit);
            }
            line(b, "Consumed in approved DPRs", qty(r.consumedToDate()) + " " + unit);
            line(b, "Remaining work still needs", qty(r.need()) + " " + unit);
            b.append("<tr><td style=\"padding:4px 0;color:#B3372E;font-weight:bold\">Still outstanding with you</td>")
             .append("<td style=\"padding:4px 0;text-align:right;color:#B3372E;font-weight:bold\">")
             .append(qty(r.excess())).append(" ").append(unit);
            if (r.excessValue() != null) {
                b.append(" &nbsp;(").append(money(r.excessValue())).append(" ")
                 .append(HtmlUtils.htmlEscape(currency)).append(")");
            }
            b.append("</td></tr></table>");
            if (!r.challanNumbers().isEmpty()) {
                b.append("<div style=\"color:#6b6b6b;font-size:11px;margin-top:6px\">Challan: ")
                 .append(HtmlUtils.htmlEscape(String.join(", ", r.challanNumbers()))).append("</div>");
            }
            if (r.bucket() == IdleStockRow.Bucket.PERSON) {
                b.append("<div style=\"color:#6b6b6b;font-size:11px;margin-top:6px\">")
                 .append("If you handed this material to another supervisor, record a return and re-issue it so the ledger follows the material.")
                 .append("</div>");
            }
            b.append("</div>");
        }

        b.append("<p><b>What to do next:</b> use it on the remaining work, return the usable balance to store so it can be re-issued to another front, or record it as damaged/scrap.</p>")
         .append("<p><a href=\"").append(href)
         .append("\" style=\"display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold\">Open Material Issues</a></p>")
         .append("<p style=\"color:#6b6b6b;font-size:12px\">Your reporting manager is copied on this message.</p>")
         .append("</div></div>");
        return b.toString();
    }

    private static void line(StringBuilder b, String label, String value) {
        b.append("<tr><td style=\"padding:4px 0;color:#4a4a4a\">").append(label).append("</td>")
         .append("<td style=\"padding:4px 0;text-align:right\">").append(value).append("</td></tr>");
    }

    // ---------------------------------------------------------------- helpers

    private static String key(IdleStockRow r) {
        return r.custodianUserId() + "|" + r.materialKey() + "|" + bucketKey(r);
    }

    private static String key(MaterialIdleAlert a) {
        return a.getCustodianUserId() + "|" + a.getMaterialKey() + "|" + a.getBucketKey();
    }

    private static String bucketKey(IdleStockRow r) {
        return r.activityId() == null ? POOL : r.activityId().toString();
    }

    private static String safeUnit(String unit) {
        return unit == null ? "" : unit;
    }

    private static String qty(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal v) {
        return String.format(java.util.Locale.ROOT, "%,.2f", v);
    }

    private static String displayName(User u) {
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
            + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isBlank() ? u.getUsername() : full;
    }

    private static AgentMailLog row(UUID projectId, String channel, UUID userId, String name,
                                    String email, String subject, String bodyHtml,
                                    String status, String detail) {
        AgentMailLog r = new AgentMailLog();
        r.setProjectId(projectId);
        r.setCategory(AgentMailLog.CAT_MATERIAL_IDLE_STOCK);
        r.setChannel(channel);
        r.setRecipientUserId(userId);
        r.setRecipientName(name);
        r.setRecipientEmail(email);
        r.setSubject(subject);
        r.setBodyHtml(bodyHtml);
        r.setStatus(status);
        r.setDetail(detail);
        return r;
    }
}
