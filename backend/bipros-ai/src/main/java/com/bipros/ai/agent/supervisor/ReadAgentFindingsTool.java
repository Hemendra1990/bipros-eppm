package com.bipros.ai.agent.supervisor;

import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Supervisor tool (read-only): read the ACTIVE findings the agents have already produced for a
 * project, optionally filtered by agent key and minimum severity. Cheaper than {@link RunAgentTool}
 * — prefer it when the user's question can be answered from existing findings. Auto-registered as a
 * {@link Tool} bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadAgentFindingsTool implements Tool {

    private final AgentMemoryService memoryService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "read_agent_findings";
    }

    @Override
    public String description() {
        return "Read the current ACTIVE findings the intelligence agents have already produced for a "
                + "project, optionally filtered by agentKey and minimum severity. Use this before "
                + "run_agent when existing findings can answer the question.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode agentKey = props.putObject("agentKey");
        agentKey.put("type", "string");
        agentKey.put("description", "Optional agent key filter, e.g. \"risk_intelligence\".");

        ObjectNode minSeverity = props.putObject("minSeverity");
        minSeverity.put("type", "string");
        minSeverity.put("description", "Optional minimum severity: INFO, LOW, MEDIUM, HIGH or CRITICAL.");

        ObjectNode projectId = props.putObject("projectId");
        projectId.put("type", "string");
        projectId.put("format", "uuid");
        projectId.put("description", "Project UUID; optional when the chat already has a project in scope.");

        schema.putArray("required");   // all optional
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = resolveProjectId(input, ctx);
        if (projectId == null) {
            return ToolResult.error("projectId is required (pass it in the input or invoke from a "
                    + "project-scoped chat)");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        String agentKeyRaw = input.path("agentKey").asText(null);
        Set<String> agentKeys = (agentKeyRaw == null || agentKeyRaw.isBlank())
                ? null : Set.of(agentKeyRaw.trim());
        String sevRaw = input.path("minSeverity").asText(null);
        Severity minSeverity = (sevRaw == null || sevRaw.isBlank()) ? null : Severity.fromString(sevRaw);

        List<AgentFinding> findings = memoryService.activeFindings(projectId, agentKeys, minSeverity);
        findings = findings.stream()
                .sorted((a, b) -> b.getSeverity().ordinal() - a.getSeverity().ordinal())
                .toList();

        ArrayNode rows = objectMapper.createArrayNode();
        for (AgentFinding f : findings) {
            ObjectNode row = rows.addObject();
            row.put("agentKey", f.getAgentKey());
            row.put("findingType", f.getFindingType());
            row.put("severity", f.getSeverity() == null ? null : f.getSeverity().name());
            row.put("confidence", f.getConfidence());
            row.put("title", f.getTitle());
            row.put("businessImpact", f.getBusinessImpact());
            row.put("recommendedAction", f.getRecommendedAction());
        }
        String summary = findings.size() + " active finding(s) for the project"
                + (agentKeys != null ? " from " + agentKeyRaw : "")
                + (minSeverity != null ? " at " + minSeverity + "+" : "") + ".";
        return ToolResult.table(summary, rows,
                new String[]{"agentKey", "findingType", "severity", "confidence", "title",
                        "businessImpact", "recommendedAction"});
    }

    private UUID resolveProjectId(JsonNode input, AiContext ctx) {
        String raw = input.path("projectId").asText(null);
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                log.debug("read_agent_findings received malformed projectId={}", raw);
            }
        }
        return ctx.projectId();
    }
}
