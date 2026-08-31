package com.bipros.api.scheduling;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.api.notification.AgentMailLog;
import com.bipros.api.notification.AgentMailLogService;
import com.bipros.api.notification.DprAlertConfig;
import com.bipros.api.notification.DprNotificationType;
import com.bipros.api.service.DprNotificationRecipientResolver;
import com.bipros.api.service.DprSlaConfig;
import com.bipros.common.notification.NotificationService;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Escalates DPRs pending approval past the SLA window (one-shot, lease-guarded). Mirrors PermitEscalationJob.
 *  Owner decision 2026-08-31: alongside the in-app notification, the approver and their manager
 *  now also get an email (managers rarely watch the bell); deliveries audit to ai.agent_mail_log. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DprApprovalSlaEscalationJob {

  private static final String JOB_NAME = "dpr_approval_sla_escalation";

  private final DailyProgressReportRepository dprRepository;
  private final ScheduledJobLeaseRepository leaseRepository;
  private final DprSlaConfig slaConfig;
  private final NotificationService notificationService;
  private final DprNotificationRecipientResolver recipientResolver;
  private final EmailService emailService;
  private final UserRepository userRepository;
  private final AgentMailLogService mailLogService;
  private final DprAlertConfig alertConfig;

  @Scheduled(fixedDelayString = "${bipros.dpr.sla-escalation-fixed-delay-ms:1800000}")
  @Transactional
  public void run() {
    Instant now = Instant.now();
    Instant until = now.plus(Duration.ofMinutes(10));
    String owner = "node-" + UUID.randomUUID();
    if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
      return;   // another node holds the lease
    }

    int slaHours = slaConfig.slaHours();
    Instant cutoff = now.minus(Duration.ofHours(slaHours));
    var overdue = dprRepository.findByApprovalStatusAndSubmittedAtBeforeAndEscalatedAtIsNull(
        DprApprovalStatus.SUBMITTED, cutoff);

    int escalated = 0;
    for (DailyProgressReport dpr : overdue) {
      try {
        long n = dpr.getSubmittedAt() != null ? Duration.between(dpr.getSubmittedAt(), now).toHours() : slaHours;
        String link = "/projects/" + dpr.getProjectId() + "/dpr?focus=" + dpr.getId();
        if (dpr.getAssignedApproverUserId() != null) {
          String body = "You haven't actioned %s's DPR for %s (%dh overdue).".formatted(
              dpr.getSupervisorName(), dpr.getActivityName(), n);
          notificationService.create(dpr.getAssignedApproverUserId(),
              DprNotificationType.DPR_APPROVAL_OVERDUE_APPROVER, "DPR approval overdue",
              body, link, dpr.getProjectId(), dpr.getId());
          sendEmail(dpr, dpr.getAssignedApproverUserId(), "DPR approval overdue", body, n, link);
        }
        for (UUID mgr : recipientResolver.escalationManagers(dpr)) {
          // The PM+admins fallback set can contain the assigned approver themself (approver
          // with no manager, or approver assigned via the PM/admin rung) — they already got
          // the direct overdue notice above, so skip the duplicate escalation copy.
          if (mgr.equals(dpr.getAssignedApproverUserId())) continue;
          String body = "A DPR by %s for %s has been awaiting approval %dh.".formatted(
              dpr.getSupervisorName(), dpr.getActivityName(), n);
          notificationService.create(mgr,
              DprNotificationType.DPR_APPROVAL_OVERDUE_ESCALATION, "DPR approval overdue (escalation)",
              body, link, dpr.getProjectId(), dpr.getId());
          sendEmail(dpr, mgr, "DPR approval overdue (escalation)", body, n, link);
        }
        dpr.setEscalatedAt(now);   // one-shot; managed entity → flushed at tx commit
        escalated++;
      } catch (Exception ex) {
        log.warn("[DprApprovalSlaEscalationJob] failed for dpr={}: {}", dpr.getId(), ex.getMessage(), ex);
      }
    }
    if (escalated > 0) log.info("DprApprovalSlaEscalationJob escalated {} DPRs", escalated);
  }

  /** Same 560px navy alert card family as the issue mails. EmailService never throws (PREVIEW
   *  without SMTP); failures land in the audit row, never break the escalation stamp. */
  private void sendEmail(DailyProgressReport dpr, UUID recipientId, String subject, String body,
                         long hoursOverdue, String link) {
    try {
      User user = userRepository.findById(recipientId).orElse(null);
      if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
        logRow(dpr, recipientId, null, null, subject, null, AgentMailLog.STATUS_SKIPPED, "user has no email");
        return;
      }
      String html = """
          <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
            <div style="background:#1F3A5F;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">%s</div>
            <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
              <p>%s</p>
              <table style="font-size:13px;border-collapse:collapse">
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Supervisor</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Activity</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Report date</td><td>%s</td></tr>
                <tr><td style="padding:2px 12px 2px 0;color:#6b6b6b">Pending</td><td style="color:#B3372E;font-weight:bold">%dh past the SLA window</td></tr>
              </table>
              <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open the approval queue</a></p>
            </div>
          </div>
          """.formatted(
          HtmlUtils.htmlEscape(subject),
          HtmlUtils.htmlEscape(body),
          HtmlUtils.htmlEscape(dpr.getSupervisorName() != null ? dpr.getSupervisorName() : "—"),
          HtmlUtils.htmlEscape(dpr.getActivityName() != null ? dpr.getActivityName() : "—"),
          dpr.getReportDate(),
          hoursOverdue,
          alertConfig.appBaseUrl() + link);
      EmailService.SendResult result =
          emailService.send(new EmailMessage(List.of(user.getEmail()), subject, html, null, null));
      logRow(dpr, recipientId, displayName(user), user.getEmail(), subject, html, result.name(), null);
    } catch (Exception ex) {
      log.warn("[DprApprovalSlaEscalationJob] email failed dpr={} recipient={}: {}",
          dpr.getId(), recipientId, ex.getMessage());
    }
  }

  private static String displayName(User u) {
    String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
        + (u.getLastName() == null ? "" : u.getLastName())).trim();
    return full.isEmpty() ? u.getUsername() : full;
  }

  private void logRow(DailyProgressReport dpr, UUID recipientId, String recipientName, String email,
                      String subject, String bodyHtml, String status, String detail) {
    AgentMailLog row = new AgentMailLog();
    row.setProjectId(dpr.getProjectId());
    row.setCategory(AgentMailLog.CAT_DPR_APPROVAL_OVERDUE);
    row.setChannel(AgentMailLog.CH_EMAIL);
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
