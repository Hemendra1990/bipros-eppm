package com.bipros.api.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ToolResult;
import com.bipros.api.dprreport.DprAgentReport;
import com.bipros.api.dprreport.DprAgentReportRepository;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.infrastructure.export.PdfReportGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The orchestrator has NO write-gate (tools fire immediately, possibly re-invoked in a
 * verification round), so {@link EmailDprReportTool} must self-guard: no send without an
 * explicit {@code confirm=true}, and no send for a report outside the caller's project
 * scope (unless ADMIN). These branches are pure guard logic and are the correctness-
 * critical part of the tool, hence covered here without any real IO.
 */
class EmailDprReportToolTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();

    private final ObjectMapper mapper = new ObjectMapper();

    private DprAgentReportRepository reportRepository;
    private ProjectRepository projectRepository;
    private PdfReportGenerator pdfGenerator;
    private EmailService emailService;
    private EmailDprReportTool tool;

    @BeforeEach
    void setUp() {
        reportRepository = mock(DprAgentReportRepository.class);
        projectRepository = mock(ProjectRepository.class);
        pdfGenerator = mock(PdfReportGenerator.class);
        emailService = mock(EmailService.class);
        tool = new EmailDprReportTool(reportRepository, projectRepository, pdfGenerator, emailService, mapper);
    }

    private static AiContext projectManagerCtx() {
        return new AiContext(UUID.randomUUID(), PROJECT_ID, "dpr-analyst", "PROJECT_MANAGER",
                "PROJECT_MANAGER", List.of(PROJECT_ID));
    }

    private DprAgentReport reportIn(UUID projectId) {
        DprAgentReport r = new DprAgentReport();
        r.setId(REPORT_ID);
        r.setProjectId(projectId);
        r.setWindowLabel("Last 7 days");
        r.setHtmlBody("<p>insights</p>");
        r.setStatus("SUCCESS");
        r.setSummary("All supervisors on track.");
        return r;
    }

    private ObjectNode input(String reportId, List<String> recipients, Boolean confirm) {
        ObjectNode n = mapper.createObjectNode();
        if (reportId != null) n.put("report_id", reportId);
        ArrayNode arr = mapper.createArrayNode();
        for (String r : recipients) arr.add(r);
        n.set("recipients", arr);
        if (confirm != null) n.put("confirm", confirm);
        return n;
    }

    @Test
    void confirmOmitted_doesNotSendAndAsksForConfirmation() {
        ToolResult result = tool.execute(
                input(REPORT_ID.toString(), List.of("pm@example.com"), null), projectManagerCtx());

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("confirm=true");
        verifyNoInteractions(emailService);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void confirmFalse_doesNotSend() {
        ToolResult result = tool.execute(
                input(REPORT_ID.toString(), List.of("pm@example.com"), false), projectManagerCtx());

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("confirm=true");
        verifyNoInteractions(emailService);
    }

    @Test
    void reportInDifferentProject_nonAdmin_errorsWithoutSending() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportIn(OTHER_PROJECT_ID)));

        ToolResult result = tool.execute(
                input(REPORT_ID.toString(), List.of("pm@example.com"), true), projectManagerCtx());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
        verifyNoInteractions(emailService);
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    void confirmedOwnedReport_sendsOnceAndSurfacesSent() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportIn(PROJECT_ID)));
        Project project = new Project();
        project.setName("Khasab Demo");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(pdfGenerator.generateReport(anyString(), anyString(), anyString()))
                .thenReturn(new byte[] {1, 2, 3});
        when(emailService.send(any(EmailMessage.class))).thenReturn(EmailService.SendResult.SENT);

        ToolResult result = tool.execute(
                input(REPORT_ID.toString(), List.of("pm@example.com"), true), projectManagerCtx());

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("Emailed");
        verify(emailService, times(1)).send(any(EmailMessage.class));
    }

    @Test
    void previewResult_surfacesPreviewNotSent() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportIn(PROJECT_ID)));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());
        when(pdfGenerator.generateReport(anyString(), anyString(), anyString()))
                .thenReturn(new byte[] {1});
        when(emailService.send(any(EmailMessage.class))).thenReturn(EmailService.SendResult.PREVIEW);

        // Distinct recipient from the SENT test so the (static, in-JVM) send-dedupe cache
        // from that test can't short-circuit this one regardless of test-method order.
        ToolResult result = tool.execute(
                input(REPORT_ID.toString(), List.of("preview@example.com"), true), projectManagerCtx());

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("Preview only");
    }
}
