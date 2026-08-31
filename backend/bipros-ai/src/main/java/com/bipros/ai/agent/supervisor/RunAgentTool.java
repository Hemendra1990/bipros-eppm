package com.bipros.ai.agent.supervisor;

import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.pipeline.AgentRunService;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.common.exception.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunAgentTool implements Tool {

    private final AgentRunService agentRunService;
    private final AgentFindingRepository agentFindingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "run_agent";
    }

    @Override
    public String description() {
        return "Run a monitoring agent (e.g. capacity_utilisation, forecasting) for a project "
                + "and return its findings.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();

        ObjectNode agentKeyNode = objectMapper.createObjectNode();
        agentKeyNode.put("type", "string");
        agentKeyNode.put("description",
                "Key of the monitoring agent to run, e.g. capacity_utilisation, forecasting, "
                        + "risk_intelligence.");
        props.set("agentKey", agentKeyNode);

        ObjectNode projectIdNode = objectMapper.createObjectNode();
        projectIdNode.put("type", "string");
        projectIdNode.put("format", "uuid");
        projectIdNode.put("description",
                "Project UUID. Optional when the conversation already has a project in scope; "
                        + "required in portfolio mode.");
        props.set("projectId", projectIdNode);

        schema.set("properties", props);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("agentKey");
        schema.set("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        String agentKey = null;
        UUID projectId = null;
        try {
            agentKey = input.path("agentKey").asText();
            if (agentKey == null || agentKey.isBlank()) {
                return ToolResult.error("agentKey is required");
            }

            projectId = resolveProjectId(input, ctx);
            if (projectId == null) {
                return ToolResult.error("projectId is required (pass it in the input or invoke "
                        + "from a project-scoped chat)");
            }

            if ((ctx.projectId() != null || projectId != null)
                    && !"ADMIN".equals(ctx.role())
                    && (ctx.scopedProjectIds() == null
                        || !ctx.scopedProjectIds().contains(projectId))) {
                throw new AccessDeniedException("project not in user scope");
            }

            log.info("Running agent '{}' for project {}", agentKey, projectId);

            AgentRunContext runCtx = AgentRunContext.manual(projectId, ctx.userId());
            AgentRun run = agentRunService.runSingle(agentKey, runCtx);
            List<AgentFinding> findings = agentFindingRepository.findByRunId(run.getId());

            ObjectNode result = objectMapper.createObjectNode();
            result.put("runId", run.getId().toString());
            result.put("agentKey", agentKey);
            result.put("status", run.getStatus().name());
            result.put("findingsCount", findings.size());

            ArrayNode findingsNode = objectMapper.createArrayNode();
            for (AgentFinding f : findings) {
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("findingType", f.getFindingType());
                fn.put("severity", f.getSeverity().name());
                fn.put("confidence", f.getConfidence());
                fn.put("title", f.getTitle());
                if (f.getSubjectRef() != null) {
                    fn.put("subjectRef", f.getSubjectRef());
                }
                findingsNode.add(fn);
            }
            result.set("findings", findingsNode);

            return ToolResult.ok(buildSummary(agentKey, findings), result);
        } catch (BusinessRuleException e) {
            return ToolResult.error(e.getMessage());
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent run failed for agentKey='{}', projectId={}", agentKey, projectId, e);
            return ToolResult.error("Agent run failed: " + e.getMessage());
        }
    }

    private UUID resolveProjectId(JsonNode input, AiContext ctx) {
        String raw = input.path("projectId").asText(null);
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                log.debug("run_agent received malformed projectId={}", raw);
            }
        }
        return ctx.projectId();
    }

    private static String buildSummary(String agentKey, List<AgentFinding> findings) {
        StringBuilder sb = new StringBuilder("Ran ").append(agentKey)
                .append(": ").append(findings.size()).append(" finding");
        if (findings.size() != 1) {
            sb.append("s");
        }
        if (findings.isEmpty()) {
            return sb.toString();
        }
        int critical = 0, high = 0, medium = 0, low = 0, info = 0;
        for (AgentFinding f : findings) {
            switch (f.getSeverity()) {
                case CRITICAL -> critical++;
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
                case INFO -> info++;
            }
        }
        List<String> parts = new ArrayList<>();
        if (critical > 0) parts.add(critical + " CRITICAL");
        if (high > 0) parts.add(high + " HIGH");
        if (medium > 0) parts.add(medium + " MEDIUM");
        if (low > 0) parts.add(low + " LOW");
        if (info > 0) parts.add(info + " INFO");
        sb.append(" (").append(String.join(", ", parts)).append(")");
        return sb.toString();
    }
}
