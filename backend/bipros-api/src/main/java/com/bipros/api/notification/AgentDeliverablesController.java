package com.bipros.api.notification;

import com.bipros.api.dprreport.DprAgentReport;
import com.bipros.api.dprreport.DprAgentReportRepository;
import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read model for the AI tab's "Agent deliverables" panel: schedule status of the scheduled
 * senders (daily report + missing-DPR alert) plus the per-recipient delivery log
 * ({@code ai.agent_mail_log}). Same permission as {@code DprReportController} — the panel
 * shows report/mail data.
 */
@RestController
@RequestMapping("/v1/projects/{projectId}/agent-deliverables")
@RequiredArgsConstructor
public class AgentDeliverablesController {

    private final DprReportConfig reportConfig;
    private final DprAlertConfig alertConfig;
    private final DprAgentReportRepository reportRepository;
    private final DprMissingAlertRunRepository missingAlertRunRepository;
    private final AgentMailLogRepository mailLogRepository;

    public record MailRow(UUID id, String category, String channel, UUID recipientUserId,
                          String recipientName, String recipientEmail, String subject,
                          String bodyHtml, UUID reportId, String status, String detail,
                          Instant sentAt) {}

    public record ReportSchedule(boolean enabled, String sendTime, String timezone, String cadence,
                                 Instant lastGeneratedAt, String lastStatus, String lastDeliveryStatus,
                                 String lastDeliveredTo, UUID lastReportId) {}

    public record MissingAlertStatus(boolean enabled, String alertTime, LocalDate lastCheckedDate,
                                     Integer lastMissingCount, Integer lastEmailsSent,
                                     Boolean lastSkippedNonWorking, Instant lastGeneratedAt) {}

    public record DeliverablesResponse(ReportSchedule reportSchedule, MissingAlertStatus missingAlert,
                                       String alertChannel, List<MailRow> mails) {}

    @GetMapping
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'REPORT.READ')")
    public ResponseEntity<ApiResponse<DeliverablesResponse>> get(@PathVariable UUID projectId) {
        DprAgentReport lastReport = reportRepository
            .findTopByProjectIdAndTriggerOrderByGeneratedAtDesc(projectId, "SCHEDULED")
            .orElse(null);
        ReportSchedule reportSchedule = new ReportSchedule(
            reportConfig.enabled(),
            reportConfig.sendTime().toString(),
            reportConfig.zone().getId(),
            reportConfig.cadence().name(),
            lastReport != null ? lastReport.getGeneratedAt() : null,
            lastReport != null ? lastReport.getStatus() : null,
            lastReport != null ? lastReport.getDeliveryStatus() : null,
            lastReport != null ? lastReport.getDeliveredTo() : null,
            lastReport != null ? lastReport.getId() : null);

        DprMissingAlertRun lastRun = missingAlertRunRepository
            .findTopByProjectIdOrderByGeneratedAtDesc(projectId)
            .orElse(null);
        MissingAlertStatus missingAlert = new MissingAlertStatus(
            alertConfig.missingAlertEnabled(),
            alertConfig.missingAlertTime().toString(),
            lastRun != null ? lastRun.getAlertDate() : null,
            lastRun != null ? lastRun.getMissingCount() : null,
            lastRun != null ? lastRun.getEmailsSent() : null,
            lastRun != null ? lastRun.isSkippedNonWorking() : null,
            lastRun != null ? lastRun.getGeneratedAt() : null);

        List<MailRow> mails = mailLogRepository
            .findTop100ByProjectIdOrderBySentAtDescIdDesc(projectId).stream()
            .map(m -> new MailRow(m.getId(), m.getCategory(), m.getChannel(), m.getRecipientUserId(),
                m.getRecipientName(), m.getRecipientEmail(), m.getSubject(), m.getBodyHtml(),
                m.getReportId(), m.getStatus(), m.getDetail(), m.getSentAt()))
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(
            new DeliverablesResponse(reportSchedule, missingAlert, alertConfig.channel(), mails)));
    }
}
