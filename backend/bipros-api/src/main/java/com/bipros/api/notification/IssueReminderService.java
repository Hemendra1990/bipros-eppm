package com.bipros.api.notification;

import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.application.service.ProjectTeamService;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Act-by SLA engine (owner decision 2026-08-31). For every issue past its {@code dueDate} and
 * still open (OPEN / IN_PROGRESS / BLOCKED — RESOLVED means the assignee acted, so reminders
 * stop even though the weekly digest still lists it awaiting close-out):
 *
 * <ul>
 *   <li><b>Reminder</b> — email + in-app to the assignee, repeated every
 *       {@code issue_reminder_every_days} (default daily) until the issue leaves the open
 *       statuses. Cadence is tracked per issue via {@code last_reminder_at}.</li>
 *   <li><b>Escalation</b> — once the issue is {@code issue_escalation_after_days} overdue,
 *       a ONE-SHOT email + in-app to the assignee's reporting manager from the project Team
 *       tab (fallback CM → PM → Project Control → admin), stamped via {@code escalated_at}
 *       so it never repeats. Daily assignee reminders continue regardless.</li>
 * </ul>
 *
 * Issues assigned by free text only (null {@code assignedToUserId}) are skipped — there is
 * nobody to address; the weekly outstanding digest still covers them. Every delivery is
 * audited to {@code ai.agent_mail_log}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueReminderService {

  /** Statuses that still demand action from the assignee. */
  static final List<IssueStatus> OPEN_STATUSES =
      List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS, IssueStatus.BLOCKED);

  private final DprIssueRepository issueRepository;
  private final DailyProgressReportRepository dprRepository;
  private final ProjectTeamService projectTeamService;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final NotificationService notificationService;
  private final AgentMailLogService mailLogService;
  private final DprAlertConfig alertConfig;
  private final DprReportConfig reportConfig;

  /**
   * Deliberately NOT one big transaction: each issue's cadence stamp is saved in its own short
   * repository transaction BEFORE its mails go out, so one bad row can neither roll back the
   * stamps of already-mailed issues (which would re-send everything on the next 15-min tick)
   * nor be re-mailed itself more than once per day. Worst case of a crash between stamp and
   * send is one skipped reminder that self-heals on the next cadence day.
   */
  public void runForProject(UUID projectId, LocalDate today) {
    List<DprIssue> overdue =
        issueRepository.findByProjectIdAndStatusInAndDueDateBefore(projectId, OPEN_STATUSES, today);
    if (overdue.isEmpty()) return;

    // Issues on a DRAFT (never-submitted) DPR must not nag anyone — the same submission gate
    // the assignment notification honours. Standalone issues (dprId null) always qualify.
    Map<UUID, DprApprovalStatus> parentStatus = new HashMap<>();
    List<UUID> dprIds = overdue.stream().map(DprIssue::getDprId).filter(Objects::nonNull).distinct().toList();
    if (!dprIds.isEmpty()) {
      for (DailyProgressReport dpr : dprRepository.findAllById(dprIds)) {
        parentStatus.put(dpr.getId(), dpr.getApprovalStatus());
      }
    }

    int everyDays = alertConfig.issueReminderEveryDays();
    int escalateAfter = alertConfig.issueEscalationAfterDays();
    Instant now = Instant.now();
    int reminded = 0;
    int escalated = 0;

    for (DprIssue issue : overdue) {
      try {
        if (issue.getAssignedToUserId() == null) continue; // free-text assignee — nobody to mail
        if (issue.getDprId() != null) {
          DprApprovalStatus st = parentStatus.get(issue.getDprId());
          if (st == null || st == DprApprovalStatus.DRAFT) continue; // unsubmitted parent
        }
        long daysOverdue = ChronoUnit.DAYS.between(issue.getDueDate(), today);

        if (reminderDue(issue, today, everyDays)) {
          issue.setLastReminderAt(now);
          issueRepository.save(issue);           // stamp first — never double-mail
          remindAssignee(issue, daysOverdue);
          reminded++;
        }

        if (issue.getEscalatedAt() == null && daysOverdue >= escalateAfter) {
          UUID target = projectTeamService
              .resolveEscalationContact(projectId, issue.getAssignedToUserId())
              .orElse(null);
          if (target != null) {
            User manager = userRepository.findById(target).orElse(null);
            issue.setEscalatedAt(now);
            issue.setEscalatedToUserId(target);
            issue.setEscalatedToName(manager != null ? displayName(manager) : null);
            issueRepository.save(issue);         // stamp first — escalation stays one-shot
            escalateToManager(issue, daysOverdue, target, manager);
            escalated++;
          }
        }
      } catch (Exception ex) {
        log.warn("[IssueReminder] issue={} failed: {}", issue.getId(), ex.getMessage(), ex);
      }
    }
    if (reminded > 0 || escalated > 0) {
      log.info("[IssueReminder] project={} reminded={} escalated={}", projectId, reminded, escalated);
    }
  }

  /** Due when never reminded, or the last reminder is at least {@code everyDays} calendar days old. */
  private boolean reminderDue(DprIssue issue, LocalDate today, int everyDays) {
    if (issue.getLastReminderAt() == null) return true;
    LocalDate lastDate = issue.getLastReminderAt().atZone(reportConfig.zone()).toLocalDate();
    return ChronoUnit.DAYS.between(lastDate, today) >= everyDays;
  }

  private void remindAssignee(DprIssue issue, long daysOverdue) {
    String link = "/projects/" + issue.getProjectId() + "/issues";
    String subject = "Issue overdue — " + issue.getTitle();
    String body = "\"%s\" is %d day%s past its Act-by date (%s). Please resolve or update it.".formatted(
        issue.getTitle(), daysOverdue, daysOverdue == 1 ? "" : "s", issue.getDueDate());

    notificationService.create(issue.getAssignedToUserId(), DprNotificationType.ISSUE_REMINDER,
        "Issue overdue", body, link, issue.getProjectId(), issue.getId());
    logRow(issue, AgentMailLog.CAT_ISSUE_REMINDER, AgentMailLog.CH_IN_APP,
        issue.getAssignedToUserId(), issue.getAssignedToName(), null, "Issue overdue", null, "SENT", null);

    User assignee = userRepository.findById(issue.getAssignedToUserId()).orElse(null);
    if (assignee == null || assignee.getEmail() == null || assignee.getEmail().isBlank()) {
      logRow(issue, AgentMailLog.CAT_ISSUE_REMINDER, AgentMailLog.CH_EMAIL,
          issue.getAssignedToUserId(), issue.getAssignedToName(), null, subject, null,
          AgentMailLog.STATUS_SKIPPED, "user has no email");
      return;
    }
    String html = overdueCard("Issue overdue — action needed",
        "This issue is assigned to you and is past its Act-by date.", issue, daysOverdue, null);
    EmailService.SendResult result =
        emailService.send(new EmailMessage(List.of(assignee.getEmail()), subject, html, null, null));
    logRow(issue, AgentMailLog.CAT_ISSUE_REMINDER, AgentMailLog.CH_EMAIL,
        issue.getAssignedToUserId(), issue.getAssignedToName(), assignee.getEmail(), subject, html,
        result.name(), null);
  }

  private void escalateToManager(DprIssue issue, long daysOverdue, UUID targetUserId, User manager) {
    String link = "/projects/" + issue.getProjectId() + "/issues";
    String subject = "Issue escalation — " + issue.getTitle();
    String body = "\"%s\" assigned to %s is %d day%s past its Act-by date (%s) and still open.".formatted(
        issue.getTitle(), issue.getAssignedToName() != null ? issue.getAssignedToName() : "—",
        daysOverdue, daysOverdue == 1 ? "" : "s", issue.getDueDate());

    notificationService.create(targetUserId, DprNotificationType.ISSUE_ESCALATED,
        "Overdue issue escalated to you", body, link, issue.getProjectId(), issue.getId());
    logRow(issue, AgentMailLog.CAT_ISSUE_ESCALATION, AgentMailLog.CH_IN_APP,
        targetUserId, manager != null ? displayName(manager) : null, null,
        "Overdue issue escalated to you", null, "SENT", null);

    if (manager == null || manager.getEmail() == null || manager.getEmail().isBlank()) {
      logRow(issue, AgentMailLog.CAT_ISSUE_ESCALATION, AgentMailLog.CH_EMAIL,
          targetUserId, manager != null ? displayName(manager) : null, null, subject, null,
          AgentMailLog.STATUS_SKIPPED, "user has no email");
      return;
    }
    String html = overdueCard("Overdue issue escalated to you",
        "An issue in your reporting line has not been actioned within its time frame.",
        issue, daysOverdue, issue.getAssignedToName());
    EmailService.SendResult result =
        emailService.send(new EmailMessage(List.of(manager.getEmail()), subject, html, null, null));
    logRow(issue, AgentMailLog.CAT_ISSUE_ESCALATION, AgentMailLog.CH_EMAIL,
        targetUserId, displayName(manager), manager.getEmail(), subject, html, result.name(), null);
  }

  /** Same 560px navy alert card family as the issue-assignment email, with overdue facts in red. */
  private String overdueCard(String heading, String intro, DprIssue issue, long daysOverdue,
                             String assigneeName) {
    String assigneeRow = assigneeName != null
        ? "<tr><td style=\"padding:2px 12px 2px 0;color:#6b6b6b\">Assigned to</td><td>%s</td></tr>"
            .formatted(HtmlUtils.htmlEscape(assigneeName))
        : "";
    return """
        <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
          <div style="background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">%s</div>
          <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
            <p>%s</p>
            <p><b>%s</b></p>
            <table style="font-size:13px;border-collapse:collapse">
              %s
              <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Act by</td><td style="color:#B3372E;font-weight:bold">%s (%d day%s overdue)</td></tr>
              <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Category</td><td>%s</td></tr>
              <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Severity</td><td>%s</td></tr>
              <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Status</td><td>%s</td></tr>
              <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Activity</td><td>%s</td></tr>
            </table>
            %s
            <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open the Issues page</a></p>
          </div>
        </div>
        """.formatted(
        HtmlUtils.htmlEscape(heading),
        HtmlUtils.htmlEscape(intro),
        HtmlUtils.htmlEscape(issue.getTitle()),
        assigneeRow,
        issue.getDueDate(), daysOverdue, daysOverdue == 1 ? "" : "s",
        issue.getCategory(), issue.getSeverity(), issue.getStatus(),
        HtmlUtils.htmlEscape(issue.getActivityName() != null ? issue.getActivityName() : "—"),
        issue.getDescription() != null && !issue.getDescription().isBlank()
            ? "<p style=\"background:#faf7f0;padding:10px 14px;border-radius:6px\">"
                + HtmlUtils.htmlEscape(issue.getDescription()) + "</p>"
            : "",
        alertConfig.appBaseUrl() + "/projects/" + issue.getProjectId() + "/issues");
  }

  private static String displayName(User u) {
    String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
        + (u.getLastName() == null ? "" : u.getLastName())).trim();
    return full.isEmpty() ? u.getUsername() : full;
  }

  private void logRow(DprIssue issue, String category, String channel, UUID recipientId,
                      String recipientName, String email, String subject, String bodyHtml,
                      String status, String detail) {
    AgentMailLog row = new AgentMailLog();
    row.setProjectId(issue.getProjectId());
    row.setCategory(category);
    row.setChannel(channel);
    row.setRecipientUserId(recipientId);
    row.setRecipientName(recipientName);
    row.setRecipientEmail(email);
    row.setSubject(subject);
    row.setBodyHtml(bodyHtml);
    row.setStatus(status);
    row.setDetail(detail);
    mailLogService.log(row);
  }
}
