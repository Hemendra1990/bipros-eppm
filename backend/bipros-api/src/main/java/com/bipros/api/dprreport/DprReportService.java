package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.InsightsResponse;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.api.notification.DprNotificationType;
import com.bipros.common.notification.NotificationService;
import com.bipros.reporting.infrastructure.export.PdfReportGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service("dprReportAgentService") // explicit bean name (historically avoided a clash with a since-removed reporting-module DprReportService)
@RequiredArgsConstructor
@Slf4j
public class DprReportService {
    private final DprReportSnapshotCollector collector;
    private final DprReportMetricsCalculator metricsCalculator;
    private final DprReportGenerator generator;
    private final DprReportVerifier verifier;
    private final DprReportHtmlRenderer htmlRenderer;
    private final DprReportRecipientResolver recipientResolver;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final PdfReportGenerator pdfGenerator;
    private final DprAgentReportRepository reportRepository;
    private final com.bipros.api.notification.SupervisorCapacityMailService supervisorCapacityMail;
    private final com.bipros.api.notification.AgentMailLogService mailLogService;
    private final com.bipros.api.notification.DprAlertConfig alertConfig;
    private final com.bipros.ai.agent.domain.AgentFindingRepository findingRepository;
    private final ObjectMapper objectMapper;

    public DprAgentReport generate(ReportRequest req) {
        DprAgentReport row = DprAgentReport.builder()
            .projectId(req.projectId()).trigger(req.trigger())
            .windowFrom(req.from()).windowTo(req.to()).windowLabel(req.windowLabel())
            .generatedAt(Instant.now()).requestedByUserId(req.requestedByUserId())
            .build();
        try {
            var snapshot = collector.collect(req);
            var metrics = metricsCalculator.compute(snapshot);
            // EVM-agent-row 2026-08-11 ("dash board should be available"): the mail's section 9
            // links straight to the project's EVM tab.
            metrics.evmDashboardUrl = alertConfig.appBaseUrl() + "/projects/" + req.projectId() + "/evm";
            InsightsResponse llm = generator.generate(snapshot, metrics);
            var verify = verifier.verify(llm, metrics.allowedNumbers);
            InsightsResponse finalResponse = verify.sanitized();

            // Port addition 2026-08-05: fold the AI board's active Critical/High findings into the
            // report — the morning email carries the day's record AND the agents' current view.
            List<com.bipros.ai.agent.domain.AgentFinding> agentFlags =
                findingRepository.findByProjectIdAndStatus(req.projectId(),
                        com.bipros.ai.agent.domain.FindingStatus.ACTIVE).stream()
                    .filter(f -> f.getSeverity() == com.bipros.ai.agent.core.Severity.CRITICAL
                            || f.getSeverity() == com.bipros.ai.agent.core.Severity.HIGH)
                    .sorted((a, b) -> b.getSeverity().ordinal() - a.getSeverity().ordinal())
                    .limit(8)
                    .toList();

            String html = htmlRenderer.render(finalResponse, metrics, req.windowLabel(), agentFlags);
            row.setStatus(verify.clean() ? "SUCCESS" : "PARTIAL");
            row.setSummary(finalResponse.summary());
            row.setInsightsJson(objectMapper.writeValueAsString(finalResponse));
            row.setHtmlBody(html);
            row.setFiltersJson(objectMapper.writeValueAsString(req));

            // deliver
            List<String> recipients = recipientResolver.resolveEmails(req);
            byte[] pdf = pdfGenerator.generateBranded(metrics.projectName + " — Daily Project Report", html);
            String subject = "Daily Project Report — " + metrics.projectName + " (" + req.windowLabel() + ")";
            var sendResult = emailService.send(new EmailMessage(recipients, subject, html,
                "daily-project-report.pdf", pdf));
            row.setDeliveredTo(String.join(",", recipients));
            row.setDeliveryStatus(sendResult.name());

            DprAgentReport saved = reportRepository.save(row);
            // Delivery log — one EMAIL row per recipient; body stays on the stored report row.
            for (String email : recipients) {
                logDelivery(req.projectId(), com.bipros.api.notification.AgentMailLog.CH_EMAIL,
                    null, email, subject, saved.getId(), sendResult.name());
            }
            if (req.deliverInApp()) notifyRecipients(req, saved);
            // Capacity-agent-row addition 2026-08-10: per-boss supervisor capacity mails ride
            // the scheduled send only (inherits its once-per-day due logic); a mail failure
            // must never fail the already-delivered report.
            if ("SCHEDULED".equals(req.trigger())) {
                try {
                    supervisorCapacityMail.send(req, snapshot, metrics);
                } catch (Exception mailEx) {
                    log.warn("[DprReportService] supervisor capacity mails failed project={}: {}",
                        req.projectId(), mailEx.getMessage(), mailEx);
                }
            }
            return saved;
        } catch (Exception e) {
            log.warn("[DprReportService] generation failed project={}: {}", req.projectId(), e.getMessage(), e);
            row.setStatus("FAILED");
            row.setErrorMessage(e.getMessage());
            return reportRepository.save(row);
        }
    }

    private void logDelivery(UUID projectId, String channel, UUID userId, String email,
                             String subject, UUID reportId, String status) {
        var logRow = new com.bipros.api.notification.AgentMailLog();
        logRow.setProjectId(projectId);
        logRow.setCategory(com.bipros.api.notification.AgentMailLog.CAT_DPR_REPORT);
        logRow.setChannel(channel);
        logRow.setRecipientUserId(userId);
        logRow.setRecipientEmail(email);
        logRow.setSubject(subject);
        logRow.setReportId(reportId);
        logRow.setStatus(status);
        mailLogService.log(logRow);
    }

    private void notifyRecipients(ReportRequest req, DprAgentReport saved) {
        String link = "/projects/" + req.projectId() + "/dpr-reports?report=" + saved.getId();
        Set<UUID> notified = new LinkedHashSet<>();
        for (UUID userId : recipientResolver.resolveRecipientUserIds(req)) {
            notificationService.create(userId, DprNotificationType.DPR_REPORT_READY,
                "DPR report ready", "DPR insights report for " + req.windowLabel() + " is ready.",
                link, req.projectId(), saved.getId());
            logDelivery(req.projectId(), com.bipros.api.notification.AgentMailLog.CH_IN_APP,
                userId, null, "DPR report ready", saved.getId(), "SENT");
            notified.add(userId);
        }
        // Scheduled runs have no requestedByUserId; on-demand runs still get notified even if
        // the requester isn't the resolved PM/CM (e.g. an admin ran it on-demand).
        if (req.requestedByUserId() != null && !notified.contains(req.requestedByUserId())) {
            notificationService.create(req.requestedByUserId(), DprNotificationType.DPR_REPORT_READY,
                "DPR report ready", "DPR insights report for " + req.windowLabel() + " is ready.",
                link, req.projectId(), saved.getId());
        }
    }
}
