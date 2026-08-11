package com.bipros.api.notification;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.resource.application.dto.MaterialBalanceRow;
import com.bipros.resource.application.service.MaterialBalanceService;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Weekly material short-supply digest (AI Agent sheet, Material row: "Identify the materials
 * with short supply and lower balance during the week/month with a report. Email to be given
 * to the designated staff").
 *
 * <p>Short supply per {@link MaterialBalanceService#shortages}: closing balance below the
 * catalogue minimum stock when one is set, else days-of-cover (closing ÷ recent daily burn)
 * below the configured threshold. Untracked projects (no store data) are recorded as evaluated
 * but send nothing — there is no balance to alert on. Recipients = PROJECT_CONTROL seats (PM
 * fallback), the same "designated staff" convention as the other agent mails. Every delivery
 * lands in {@code ai.agent_mail_log}; one {@link MaterialShortageRun} row per (project, ISO
 * week) is the idempotence guard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialShortageService {

    private final MaterialBalanceService balanceService;
    private final ProjectTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final DprAlertConfig alertConfig;
    private final AgentMailLogService mailLogService;
    private final MaterialShortageRunRepository runRepository;

    @Transactional
    public void runForProject(Project project, LocalDate weekStart) {
        UUID projectId = project.getId();
        var availability = balanceService.availability(
            projectId, null, LocalDate.now(), alertConfig.materialShortageDaysCover());
        if (!availability.tracked()) {
            saveRun(projectId, weekStart, 0, 0, false);
            return;
        }
        List<MaterialBalanceRow> shortages = availability.rows().stream()
            .filter(r -> r.alerts().contains("BELOW_MIN_STOCK") || r.alerts().contains("LOW_COVER"))
            .toList();
        if (shortages.isEmpty()) {
            saveRun(projectId, weekStart, 0, 0, true);
            return;
        }

        Set<UUID> recipients = new LinkedHashSet<>(controlSeatIds(projectId));
        String link = "/projects/" + projectId + "/reports/material-consumption";
        String subject = "Material short supply — %s · %d material(s) low"
            .formatted(project.getName(), shortages.size());
        String html = shortageHtml(project, shortages, alertConfig.appBaseUrl() + link);
        String body = "%d material(s) in short supply. Weekly digest.".formatted(shortages.size());

        int emailsSent = 0;
        for (UUID userId : recipients) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            try {
                notificationService.create(userId, DprNotificationType.MATERIAL_SHORT_SUPPLY,
                    "Material short supply", body, link, projectId, null);
                mailLogService.log(row(projectId, AgentMailLog.CH_IN_APP, userId, displayName(user),
                    null, "Material short supply", null, "SENT", null));
            } catch (Exception ex) {
                log.warn("[MaterialShortage] in-app notification failed for {}: {}", userId, ex.getMessage());
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                mailLogService.log(row(projectId, AgentMailLog.CH_EMAIL, userId, displayName(user),
                    null, subject, null, AgentMailLog.STATUS_SKIPPED, "user has no email"));
                continue;
            }
            EmailService.SendResult result = emailService.send(
                new EmailMessage(List.of(user.getEmail()), subject, html, null, null));
            mailLogService.log(row(projectId, AgentMailLog.CH_EMAIL, userId, displayName(user),
                user.getEmail(), subject, html, result.name(), null));
            if (result == EmailService.SendResult.SENT) emailsSent++;
        }
        saveRun(projectId, weekStart, shortages.size(), emailsSent, true);
        log.info("[MaterialShortage] project={} weekStart={} shortages={} emails={}",
            projectId, weekStart, shortages.size(), emailsSent);
    }

    // ---------------------------------------------------------------- content

    private String shortageHtml(Project project, List<MaterialBalanceRow> shortages, String href) {
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:680px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden\">")
         .append("<div style=\"background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold\">Material short supply — ")
         .append(HtmlUtils.htmlEscape(project.getName() != null ? project.getName() : "Project"))
         .append("</div><div style=\"padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6\">")
         .append("<p style=\"color:#B3372E;font-weight:bold\">").append(shortages.size())
         .append(" material(s) below minimum stock or low on cover:</p>")
         .append("<table style=\"width:100%;border-collapse:collapse;font-size:12px\">")
         .append("<tr style=\"text-align:left;color:#6b6b6b\">")
         .append("<th style=\"padding:4px 8px\">Material</th><th style=\"padding:4px 8px\">Unit</th>")
         .append("<th style=\"padding:4px 8px;text-align:right\">Closing balance</th>")
         .append("<th style=\"padding:4px 8px;text-align:right\">Min stock</th>")
         .append("<th style=\"padding:4px 8px;text-align:right\">Days cover</th>")
         .append("<th style=\"padding:4px 8px\">Alert</th></tr>");
        for (MaterialBalanceRow r : shortages) {
            boolean belowMin = r.alerts().contains("BELOW_MIN_STOCK");
            b.append("<tr style=\"border-top:1px solid #eee;background:#fdf3f3\">")
             .append("<td style=\"padding:4px 8px\"><b>").append(HtmlUtils.htmlEscape(r.materialName())).append("</b></td>")
             .append("<td style=\"padding:4px 8px\">").append(HtmlUtils.htmlEscape(r.unit() == null ? "—" : r.unit())).append("</td>")
             .append("<td style=\"padding:4px 8px;text-align:right\">")
             .append(r.storeClosing() != null ? r.storeClosing().stripTrailingZeros().toPlainString() : "—").append("</td>")
             .append("<td style=\"padding:4px 8px;text-align:right\">")
             .append(r.minStockLevel() != null ? r.minStockLevel().stripTrailingZeros().toPlainString() : "—").append("</td>")
             .append("<td style=\"padding:4px 8px;text-align:right\">")
             .append(r.daysOfCover() != null ? r.daysOfCover().toPlainString() : "—").append("</td>")
             .append("<td style=\"padding:4px 8px;color:#B3372E;font-weight:bold\">")
             .append(belowMin ? "Below min stock" : "Low days-of-cover").append("</td></tr>");
        }
        b.append("</table>")
         .append("<p><a href=\"").append(href)
         .append("\" style=\"display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold\">Open the Material Consumption Report</a></p>")
         .append("</div></div>");
        return b.toString();
    }

    // ---------------------------------------------------------------- helpers
    // (same seat/name conventions as IssueDigestService)

    private List<UUID> controlSeatIds(UUID projectId) {
        List<UUID> ids = new java.util.ArrayList<>();
        for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PROJECT_CONTROL)) {
            if (m.getUserId() != null) ids.add(m.getUserId());
        }
        if (ids.isEmpty()) {
            for (ProjectTeamMember m : teamRepository.findByProjectIdAndRole(projectId, ProjectRole.PM)) {
                if (m.getUserId() != null) ids.add(m.getUserId());
            }
        }
        return ids;
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
        r.setCategory(AgentMailLog.CAT_MATERIAL_SHORT_SUPPLY);
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

    private void saveRun(UUID projectId, LocalDate weekStart, int shortageCount, int emails, boolean tracked) {
        MaterialShortageRun run = new MaterialShortageRun();
        run.setProjectId(projectId);
        run.setWeekStart(weekStart);
        run.setShortageCount(shortageCount);
        run.setEmailsSent(emails);
        run.setTracked(tracked);
        run.setGeneratedAt(Instant.now());
        runRepository.save(run);
    }
}
