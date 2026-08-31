package com.bipros.api.notification;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectRole;
import com.bipros.project.domain.model.ProjectTeamMember;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.project.domain.repository.ProjectTeamRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Weekly outstanding-issues digest (AI Agent sheet, Issues row: "Email trigger to the
 * designated staff &amp; project control team … to notify the outstanding issues on weekly
 * basis duly highlighting critical issues").
 *
 * <p>Outstanding = every issue not CLOSED and not CANCELLED (a RESOLVED issue still awaits
 * project control's close-out, so it stays on the list). Critical issues render in their own
 * red section on top. Recipients = PROJECT_CONTROL seats (PM fallback — same convention as
 * {@link DprMissingAlertService}) ∪ the linked assignees of outstanding issues. Every
 * delivery (email + in-app mirror, incl. SKIPPED no-email recipients) lands in
 * {@code ai.agent_mail_log}; one {@link IssueDigestRun} row per (project, ISO week) is the
 * idempotence guard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueDigestService {

    private final DprIssueRepository issueRepository;
    private final ProjectTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final DprAlertConfig alertConfig;
    private final AgentMailLogService mailLogService;
    private final IssueDigestRunRepository runRepository;

    @Transactional
    public void runForProject(Project project, LocalDate weekStart) {
        UUID projectId = project.getId();
        List<DprIssue> outstanding = issueRepository.findByProjectIdOrderByOpenedAtDesc(projectId).stream()
            .filter(i -> i.getStatus() != IssueStatus.CLOSED && i.getStatus() != IssueStatus.CANCELLED)
            .sorted(Comparator
                .comparing((DprIssue i) -> i.getSeverity() == null ? 99 : -i.getSeverity().ordinal())
                .thenComparing(i -> i.getDueDate() == null ? LocalDate.MAX : i.getDueDate())
                .thenComparing(DprIssue::getOpenedAt))
            .toList();
        int critical = (int) outstanding.stream()
            .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
        if (outstanding.isEmpty()) {
            saveRun(projectId, weekStart, 0, 0, 0);
            return;
        }
        if ("WHATSAPP".equals(alertConfig.channel())) {
            log.info("[IssueDigest] dpr_alert_channel=WHATSAPP but no provider is configured — falling back to email");
        }

        // Recipients: PC seats (PM fallback) + linked assignees of the outstanding issues.
        Set<UUID> recipients = new LinkedHashSet<>(controlSeatIds(projectId));
        outstanding.stream().map(DprIssue::getAssignedToUserId)
            .filter(java.util.Objects::nonNull).forEach(recipients::add);

        String link = "/projects/" + projectId + "/issues";
        String subject = "Outstanding issues — %s · %d open, %d critical"
            .formatted(project.getName(), outstanding.size(), critical);
        String html = digestHtml(project, outstanding, critical, alertConfig.appBaseUrl() + link);
        String body = "%d outstanding issue(s), %d critical. Weekly digest.".formatted(outstanding.size(), critical);

        int emailsSent = 0;
        for (UUID userId : recipients) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;
            try {
                notificationService.create(userId, DprNotificationType.OUTSTANDING_ISSUES_DIGEST,
                    "Outstanding issues digest", body, link, projectId, null);
                mailLogService.log(row(projectId, AgentMailLog.CH_IN_APP, userId, displayName(user),
                    null, "Outstanding issues digest", null, "SENT", null));
            } catch (Exception ex) {
                log.warn("[IssueDigest] in-app notification failed for {}: {}", userId, ex.getMessage());
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
        saveRun(projectId, weekStart, outstanding.size(), critical, emailsSent);
        log.info("[IssueDigest] project={} weekStart={} outstanding={} critical={} emails={}",
            projectId, weekStart, outstanding.size(), critical, emailsSent);
    }

    // ---------------------------------------------------------------- content

    private String digestHtml(Project project, List<DprIssue> outstanding, int critical, String href) {
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:680px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden\">")
         .append("<div style=\"background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold\">Outstanding issues — ")
         .append(HtmlUtils.htmlEscape(project.getName() != null ? project.getName() : "Project"))
         .append("</div><div style=\"padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6\">");
        if (critical > 0) {
            b.append("<p style=\"color:#B3372E;font-weight:bold\">").append(critical)
             .append(" CRITICAL issue(s) need attention:</p>");
            issueTable(b, outstanding.stream()
                .filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).toList(), true);
        }
        List<DprIssue> rest = outstanding.stream()
            .filter(i -> i.getSeverity() != IssueSeverity.CRITICAL).toList();
        if (!rest.isEmpty()) {
            b.append("<p style=\"margin-top:14px\"><b>All other outstanding issues:</b></p>");
            issueTable(b, rest, false);
        }
        b.append("<p><a href=\"").append(href)
         .append("\" style=\"display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold\">Open the Issues page</a></p>")
         .append("</div></div>");
        return b.toString();
    }

    private void issueTable(StringBuilder b, List<DprIssue> issues, boolean criticalSection) {
        LocalDate today = LocalDate.now();
        b.append("<table style=\"width:100%;border-collapse:collapse;font-size:12px\">")
         .append("<tr style=\"text-align:left;color:#6b6b6b\">")
         .append("<th style=\"padding:4px 8px\">Issue</th><th style=\"padding:4px 8px\">Category</th>")
         .append("<th style=\"padding:4px 8px\">Severity</th><th style=\"padding:4px 8px\">Status</th>")
         .append("<th style=\"padding:4px 8px\">Assignee</th><th style=\"padding:4px 8px\">Act by</th>")
         .append("<th style=\"padding:4px 8px;text-align:right\">Days open</th></tr>");
        for (DprIssue i : issues) {
            boolean overdue = i.getDueDate() != null && i.getDueDate().isBefore(today);
            long daysOpen = i.getOpenedAt() != null
                ? ChronoUnit.DAYS.between(i.getOpenedAt(), Instant.now()) : 0;
            b.append("<tr style=\"border-top:1px solid #eee")
             .append(criticalSection ? ";background:#fdf3f3" : "").append("\">")
             .append("<td style=\"padding:4px 8px\"><b>").append(HtmlUtils.htmlEscape(i.getTitle())).append("</b>")
             .append(i.isInterventionRequired()
                 ? " <span style=\"color:#B3372E;font-size:10px;font-weight:bold\">[INTERVENTION]</span>" : "")
             .append("</td>")
             .append("<td style=\"padding:4px 8px\">").append(i.getCategory()).append("</td>")
             .append("<td style=\"padding:4px 8px\">").append(i.getSeverity()).append("</td>")
             .append("<td style=\"padding:4px 8px\">").append(i.getStatus()).append("</td>")
             .append("<td style=\"padding:4px 8px\">")
             .append(HtmlUtils.htmlEscape(i.getAssignedToName() != null ? i.getAssignedToName() : "—")).append("</td>")
             .append("<td style=\"padding:4px 8px").append(overdue ? ";color:#B3372E;font-weight:bold" : "").append("\">")
             .append(i.getDueDate() != null ? i.getDueDate() + (overdue ? " (overdue)" : "") : "—").append("</td>")
             .append("<td style=\"padding:4px 8px;text-align:right\">").append(daysOpen).append("</td></tr>");
        }
        b.append("</table>");
    }

    // ---------------------------------------------------------------- helpers
    // (same seat/name conventions as DprMissingAlertService — kept local so that tested
    // service stays untouched)

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
        r.setCategory(AgentMailLog.CAT_OUTSTANDING_ISSUES);
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

    private void saveRun(UUID projectId, LocalDate weekStart, int outstanding, int critical, int emails) {
        IssueDigestRun run = new IssueDigestRun();
        run.setProjectId(projectId);
        run.setWeekStart(weekStart);
        run.setOutstandingCount(outstanding);
        run.setCriticalCount(critical);
        run.setEmailsSent(emails);
        run.setGeneratedAt(Instant.now());
        runRepository.save(run);
    }
}
