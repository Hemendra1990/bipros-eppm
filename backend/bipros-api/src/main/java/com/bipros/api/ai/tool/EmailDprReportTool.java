package com.bipros.api.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.api.dprreport.DprAgentReport;
import com.bipros.api.dprreport.DprAgentReportRepository;
import com.bipros.api.email.EmailMessage;
import com.bipros.api.email.EmailService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.infrastructure.export.PdfReportGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails a previously-saved DPR report (PDF attachment) to given recipients.
 *
 * <p>This is the one side-effecting DPR Analyst tool. The orchestrator has no write-gate
 * (tools fire immediately, possibly re-invoked in a verification round), so this tool
 * self-guards: it refuses to send unless {@code confirm=true} is explicitly passed (the
 * LLM is instructed to confirm the recipient with the user first and only then re-call
 * with {@code confirm=true}), and it only ever sends a report that belongs to the caller's
 * project in scope (or any project, for ADMIN).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDprReportTool implements Tool {

    private final DprAgentReportRepository reportRepository;
    private final ProjectRepository projectRepository;
    private final PdfReportGenerator pdfGenerator;
    private final EmailService emailService;
    private final ObjectMapper mapper;

    /**
     * Idempotency guard against the orchestrator's verification round re-invoking this
     * side-effecting tool: after a real send, an identical (report, recipients) call within
     * this window is treated as a no-op ("already sent") so we never double-email a real inbox.
     * In-memory + short-lived on purpose — a genuine resend hours/days later is legitimate.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> RECENT_SENDS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEDUPE_WINDOW_MS = 120_000L;

    @Override
    public String name() {
        return "email_dpr_report";
    }

    @Override
    public String description() {
        return "Email a previously-saved DPR report (PDF) to given recipients. Requires "
                + "confirm=true; always confirm the recipient with the user first — call "
                + "this once without confirm=true (or with it omitted) to get a "
                + "confirmation prompt, then call again with confirm=true only after the "
                + "user has agreed. Use list_dpr_reports or analyze_dpr to get a report_id "
                + "first.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = mapper.createObjectNode();

        props.set("report_id", strSchema(
                "UUID of a previously generated DPR report (from list_dpr_reports or analyze_dpr)."));

        ObjectNode recipients = mapper.createObjectNode();
        recipients.put("type", "array");
        recipients.put("description",
                "Recipient email addresses. Always confirm these with the user before "
                        + "calling with confirm=true.");
        recipients.set("items", mapper.createObjectNode().put("type", "string"));
        props.set("recipients", recipients);

        ObjectNode confirm = mapper.createObjectNode();
        confirm.put("type", "boolean");
        confirm.put("description",
                "Must be true to actually send. Omit or set false to only get a "
                        + "confirmation prompt back — nothing is sent in that case.");
        props.set("confirm", confirm);

        schema.set("properties", props);

        ArrayNode required = mapper.createArrayNode();
        required.add("report_id");
        required.add("recipients");
        schema.set("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        List<String> recipients = parseRecipients(input);
        if (recipients.isEmpty()) {
            return ToolResult.error("No recipient email given.");
        }

        if (!input.path("confirm").asBoolean(false)) {
            return ToolResult.ok(
                    "Not sent — please confirm sending to " + String.join(", ", recipients)
                            + " with the user, then call again with confirm=true.",
                    null);
        }

        UUID reportId;
        try {
            String raw = text(input, "report_id");
            if (raw == null) {
                return ToolResult.error("report_id is required.");
            }
            reportId = UUID.fromString(raw);
        } catch (Exception e) {
            return ToolResult.error("Invalid report_id.");
        }

        Optional<DprAgentReport> found = reportRepository.findById(reportId)
                .filter(r -> "ADMIN".equals(ctx.role())
                        || (ctx.projectId() != null && ctx.projectId().equals(r.getProjectId())));
        if (found.isEmpty()) {
            return ToolResult.error("Report not found in this project.");
        }
        DprAgentReport report = found.get();

        // Idempotency: if this exact report+recipients was actually sent moments ago
        // (e.g. the verification round re-called us), do not send a second real email.
        long now = System.currentTimeMillis();
        RECENT_SENDS.values().removeIf(ts -> now - ts > DEDUPE_WINDOW_MS);
        String dedupeKey = report.getId() + "|"
                + recipients.stream().sorted().collect(java.util.stream.Collectors.joining(","));
        Long last = RECENT_SENDS.get(dedupeKey);
        if (last != null && now - last < DEDUPE_WINDOW_MS) {
            ObjectNode w = mapper.createObjectNode();
            w.put("report_id", report.getId().toString());
            w.put("recipients", String.join(", ", recipients));
            w.put("delivery_status", "ALREADY_SENT");
            return ToolResult.ok("Already emailed this report to " + String.join(", ", recipients)
                    + " a moment ago — not sending it again.", w);
        }

        try {
            String projectName = projectRepository.findById(report.getProjectId())
                    .map(Project::getName)
                    .orElse("Project");

            byte[] pdf = pdfGenerator.generateReport(
                    projectName + " — Daily DPR Report", report.getHtmlBody(), projectName);

            EmailService.SendResult result = emailService.send(new EmailMessage(
                    recipients,
                    "Daily DPR Report — " + projectName + " (" + report.getWindowLabel() + ")",
                    report.getHtmlBody(),
                    "daily-dpr-report.pdf",
                    pdf));

            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("report_id", report.getId().toString());
            wrapper.put("recipients", String.join(", ", recipients));
            wrapper.put("delivery_status", result.name());

            return switch (result) {
                case SENT -> {
                    RECENT_SENDS.put(dedupeKey, now); // only real sends are de-duped
                    yield ToolResult.ok(
                            "Emailed the report to " + String.join(", ", recipients) + ".", wrapper);
                }
                case PREVIEW -> ToolResult.ok(
                        "Preview only — SMTP is not configured, so nothing was actually sent. "
                                + "(Report is ready.)",
                        wrapper);
                case FAILED -> ToolResult.error("Email send failed.");
            };
        } catch (Exception e) {
            log.warn("email_dpr_report failed for report {}", reportId, e);
            return ToolResult.error("Failed to email report: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────── helpers

    private List<String> parseRecipients(JsonNode input) {
        List<String> out = new ArrayList<>();
        JsonNode arr = input.path("recipients");
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                String s = n.asText(null);
                if (s != null && !s.isBlank()) out.add(s.trim());
            }
        }
        return out;
    }

    private static String text(JsonNode in, String field) {
        if (in == null) return null;
        JsonNode n = in.path(field);
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText(null);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ObjectNode strSchema(String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "string");
        n.put("description", description);
        return n;
    }
}
