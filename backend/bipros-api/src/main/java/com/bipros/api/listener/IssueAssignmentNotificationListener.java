package com.bipros.api.listener;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.api.notification.AgentMailLog;
import com.bipros.api.notification.AgentMailLogService;
import com.bipros.api.notification.DprAlertConfig;
import com.bipros.api.notification.DprNotificationType;
import com.bipros.common.event.IssueAssignedEvent;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Assignment auto-notification (AI Agent sheet, Issues row: "the designated project control
 * staff should assign the responsible person along with time frame to act on it and auto email
 * to be given to the related people"). Fires AFTER_COMMIT of an Issues-surface create/patch
 * that set or changed the assignee; the responsible person gets an email + in-app mirror, and
 * every delivery lands in {@code ai.agent_mail_log} (SKIPPED when the user has no email).
 * WhatsApp channel value falls back to email until a provider is configured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueAssignmentNotificationListener {

  private final DprIssueRepository issueRepository;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final NotificationService notificationService;
  private final DprAlertConfig alertConfig;
  private final AgentMailLogService mailLogService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onAssigned(IssueAssignedEvent e) {
    try {
      DprIssue issue = issueRepository.findByIdAndProjectId(e.issueId(), e.projectId()).orElse(null);
      if (issue == null || e.assignedToUserId() == null) return;
      if ("WHATSAPP".equals(alertConfig.channel())) {
        log.info("[IssueAssignment] dpr_alert_channel=WHATSAPP but no provider is configured — falling back to email");
      }
      String link = "/projects/" + e.projectId() + "/issues";
      String due = issue.getDueDate() != null ? issue.getDueDate().toString() : "—";
      String body = "Issue \"%s\" (%s, %s) was assigned to you. Act by: %s.".formatted(
          issue.getTitle(), issue.getCategory(), issue.getSeverity(), due);

      notificationService.create(e.assignedToUserId(), DprNotificationType.ISSUE_ASSIGNED,
          "Issue assigned to you", body, link, e.projectId(), issue.getId());
      logRow(issue, AgentMailLog.CH_IN_APP, null, "Issue assigned to you", null, "SENT", null);

      User user = userRepository.findById(e.assignedToUserId()).orElse(null);
      String subject = "Issue assigned — " + issue.getTitle();
      if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
        log.info("[IssueAssignment] email skipped — assignee {} has no email", e.assignedToUserId());
        logRow(issue, AgentMailLog.CH_EMAIL, null, subject, null,
            AgentMailLog.STATUS_SKIPPED, "user has no email");
        return;
      }
      String interventionBadge = issue.isInterventionRequired()
          ? "<p style=\"background:#fdf3f3;border-left:4px solid #B3372E;padding:8px 12px;margin:12px 0;color:#B3372E;font-weight:bold\">Next-level intervention required</p>"
          : "";
      String html = """
          <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
            <div style="background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">Issue assigned to you</div>
            <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
              <p><b>%s</b></p>
              %s
              <table style="font-size:13px;border-collapse:collapse">
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Category</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Severity</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Status</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Act by</td><td><b>%s</b></td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Activity</td><td>%s</td></tr>
              </table>
              %s
              <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open the Issues page</a></p>
            </div>
          </div>
          """.formatted(
          HtmlUtils.htmlEscape(issue.getTitle()),
          interventionBadge,
          issue.getCategory(), issue.getSeverity(), issue.getStatus(),
          HtmlUtils.htmlEscape(due),
          HtmlUtils.htmlEscape(issue.getActivityName() != null ? issue.getActivityName() : "—"),
          issue.getDescription() != null && !issue.getDescription().isBlank()
              ? "<p style=\"background:#faf7f0;padding:10px 14px;border-radius:6px\">" + HtmlUtils.htmlEscape(issue.getDescription()) + "</p>"
              : "",
          alertConfig.appBaseUrl() + link);
      EmailService.SendResult result =
          emailService.send(new EmailMessage(List.of(user.getEmail()), subject, html, null, null));
      logRow(issue, AgentMailLog.CH_EMAIL, user.getEmail(), subject, html, result.name(), null);
    } catch (Exception ex) {
      log.warn("[IssueAssignment] notification failed issue={}: {}", e.issueId(), ex.getMessage(), ex);
    }
  }

  private void logRow(DprIssue issue, String channel, String email, String subject,
                      String bodyHtml, String status, String detail) {
    AgentMailLog row = new AgentMailLog();
    row.setProjectId(issue.getProjectId());
    row.setCategory(AgentMailLog.CAT_ISSUE_ASSIGNMENT);
    row.setChannel(channel);
    row.setRecipientUserId(issue.getAssignedToUserId());
    row.setRecipientName(issue.getAssignedToName());
    row.setRecipientEmail(email);
    row.setSubject(subject);
    row.setBodyHtml(bodyHtml);
    row.setStatus(status);
    row.setDetail(detail);
    mailLogService.log(row);
  }
}
