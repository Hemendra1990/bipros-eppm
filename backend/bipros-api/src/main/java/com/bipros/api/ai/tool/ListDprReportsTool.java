package com.bipros.api.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.api.dprreport.DprAgentReport;
import com.bipros.api.dprreport.DprAgentReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lists recently generated DPR analysis reports for the project in scope, so the DPR
 * Analyst assistant can resolve "which report do you mean" before emailing/resending one
 * (see {@link EmailDprReportTool}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListDprReportsTool implements Tool {

    private final DprAgentReportRepository reportRepository;
    private final ObjectMapper mapper;

    @Override
    public String name() {
        return "list_dpr_reports";
    }

    @Override
    public String description() {
        return "List recently generated DPR analysis reports for this project (for choosing "
                + "one to email/resend). Returns up to 20 most recent reports, each with "
                + "report_id, window_label, generated_at, trigger (SCHEDULED|ON_DEMAND), "
                + "status (SUCCESS|PARTIAL|FAILED), and summary. Call this before "
                + "email_dpr_report whenever the user hasn't named a specific report, or to "
                + "disambiguate which one to send. Requires a project in scope.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("No project in scope.");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        try {
            List<DprAgentReport> reports =
                    reportRepository.findTop20ByProjectIdOrderByGeneratedAtDesc(projectId);

            ArrayNode arr = mapper.createArrayNode();
            for (DprAgentReport r : reports) {
                ObjectNode n = mapper.createObjectNode();
                n.put("report_id", r.getId() == null ? null : r.getId().toString());
                n.put("window_label", r.getWindowLabel());
                n.put("generated_at", r.getGeneratedAt() == null ? null : r.getGeneratedAt().toString());
                n.put("trigger", r.getTrigger());
                n.put("status", r.getStatus());
                n.put("summary", r.getSummary());
                arr.add(n);
            }
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.set("reports", arr);
            return ToolResult.ok(reports.size() + " report(s) found", wrapper);
        } catch (Exception e) {
            log.warn("list_dpr_reports failed", e);
            return ToolResult.error("Failed to list DPR reports: " + e.getMessage());
        }
    }
}
