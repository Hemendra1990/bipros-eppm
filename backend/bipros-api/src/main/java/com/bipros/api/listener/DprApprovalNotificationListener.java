package com.bipros.api.listener;

import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.api.notification.AgentMailLog;
import com.bipros.api.notification.AgentMailLogService;
import com.bipros.api.notification.DprAlertConfig;
import com.bipros.api.notification.DprNotificationType;
import com.bipros.api.service.DprNotificationRecipientResolver;
import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.common.notification.NotificationService;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DprApprovalNotificationListener {

  private final DailyProgressReportRepository dprRepository;
  private final NotificationService notificationService;
  private final DprNotificationRecipientResolver recipientResolver;
  private final UserRepository userRepository;
  private final EmailService emailService;
  private final DprAlertConfig alertConfig;
  private final AgentMailLogService mailLogService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDpr(DprSubmittedEvent e) {
    if (e.eventType() == DprMutationType.DELETED) return;
    DailyProgressReport dpr = dprRepository.findById(e.dprId()).orElse(null);
    if (dpr == null || dpr.getApprovalStatus() == null) return;
    String link = "/projects/" + dpr.getProjectId() + "/dpr?focus=" + dpr.getId();
    try {
      switch (dpr.getApprovalStatus()) {
        case SUBMITTED -> notifyArrived(dpr, link);
        case APPROVED -> notificationService.create(dpr.getSubmittedByUserId(),
            DprNotificationType.DPR_APPROVED, "DPR approved",
            "Your DPR for %s on %s was approved.".formatted(dpr.getActivityName(), dpr.getReportDate()),
            link, dpr.getProjectId(), dpr.getId());
        case REJECTED -> {
          notificationService.create(dpr.getSubmittedByUserId(),
              DprNotificationType.DPR_REJECTED, "DPR rejected",
              "Your DPR for %s on %s was rejected: %s. Edit and resubmit.".formatted(
                  dpr.getActivityName(), dpr.getReportDate(),
                  dpr.getRejectionReason() != null ? dpr.getRejectionReason() : "see remarks"),
              link, dpr.getProjectId(), dpr.getId());
          logDelivery(dpr, AgentMailLog.CH_IN_APP, null, null, "DPR rejected", null, "SENT");
          sendRejectionEmail(dpr, link);
        }
        default -> { /* DRAFT: no-op */ }
      }
    } catch (Exception ex) {
      log.warn("[DprApprovalNotificationListener] failed for dpr={}: {}", dpr.getId(), ex.getMessage(), ex);
    }
  }

  /**
   * Client requirement (AI Agent sheet, DPR row): alert the supervisor that their DPR was
   * rejected, clearly stating the reasons, so they re-submit with corrected data. The doc asks
   * for WhatsApp; per the owner decision (2026-08-10) delivery is EMAIL until a WhatsApp
   * provider is configured — {@code dpr_alert_channel} records the preference.
   */
  private void sendRejectionEmail(DailyProgressReport dpr, String link) {
    try {
      if ("WHATSAPP".equals(alertConfig.channel())) {
        log.info("[DprApprovalNotificationListener] dpr_alert_channel=WHATSAPP but no provider is configured — falling back to email");
      }
      UUID submitter = dpr.getSubmittedByUserId();
      if (submitter == null) return;
      User user = userRepository.findById(submitter).orElse(null);
      if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
        log.info("[DprApprovalNotificationListener] rejection email skipped — submitter {} has no email", submitter);
        return;
      }
      String reason = dpr.getRejectionReason() != null ? dpr.getRejectionReason() : "see remarks";
      String subject = "DPR rejected — %s · %s".formatted(dpr.getActivityName(), dpr.getReportDate());
      String href = alertConfig.appBaseUrl() + link;
      String html = """
          <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #e5e0d8;border-radius:8px;overflow:hidden">
            <div style="background:#8A2232;color:#fff;padding:14px 20px;font-size:16px;font-weight:bold">DPR rejected — please correct and resubmit</div>
            <div style="padding:20px;color:#2b2b2b;font-size:14px;line-height:1.6">
              <p>Your Daily Progress Report for <b>%s</b> on <b>%s</b> was rejected.</p>
              <p style="background:#faf5ef;border-left:4px solid #8A2232;padding:10px 14px;margin:14px 0"><b>Reason:</b> %s</p>
              <p>Please open the report, correct the data, and resubmit for approval.</p>
              <p><a href="%s" style="display:inline-block;background:#C9A227;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:bold">Open the DPR</a></p>
            </div>
          </div>
          """.formatted(
          HtmlUtils.htmlEscape(dpr.getActivityName() != null ? dpr.getActivityName() : "—"),
          dpr.getReportDate(),
          HtmlUtils.htmlEscape(reason),
          href);
      EmailService.SendResult result =
          emailService.send(new EmailMessage(List.of(user.getEmail()), subject, html, null, null));
      logDelivery(dpr, AgentMailLog.CH_EMAIL, displayName(user), user.getEmail(), subject, html,
          result.name());
    } catch (Exception ex) {
      log.warn("[DprApprovalNotificationListener] rejection email failed for dpr={}: {}", dpr.getId(), ex.getMessage());
    }
  }

  private void logDelivery(DailyProgressReport dpr, String channel, String name, String email,
                           String subject, String bodyHtml, String status) {
    AgentMailLog row = new AgentMailLog();
    row.setProjectId(dpr.getProjectId());
    row.setCategory(AgentMailLog.CAT_DPR_REJECTION);
    row.setChannel(channel);
    row.setRecipientUserId(dpr.getSubmittedByUserId());
    row.setRecipientName(name);
    row.setRecipientEmail(email);
    row.setSubject(subject);
    row.setBodyHtml(bodyHtml);
    row.setStatus(status);
    mailLogService.log(row);
  }

  private static String displayName(User u) {
    String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
        + (u.getLastName() == null ? "" : u.getLastName())).trim();
    return full.isBlank() ? u.getUsername() : full;
  }

  private void notifyArrived(DailyProgressReport dpr, String link) {
    String title = "DPR awaiting approval";
    String body = "DPR for %s by %s on %s is awaiting your approval.".formatted(
        dpr.getActivityName(), dpr.getSupervisorName(), dpr.getReportDate());
    for (UUID recipient : recipientResolver.arrivalRecipients(dpr)) {
      if (notificationService.existsSince(dpr.getId(),
          DprNotificationType.DPR_SUBMITTED_FOR_APPROVAL, recipient, dpr.getSubmittedAt())) {
        continue; // dedup: already notified this submission cycle
      }
      notificationService.create(recipient, DprNotificationType.DPR_SUBMITTED_FOR_APPROVAL,
          title, body, link, dpr.getProjectId(), dpr.getId());
    }
  }
}
