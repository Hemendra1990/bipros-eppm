package com.bipros.ai.tool.risk;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrigger;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import com.bipros.risk.domain.repository.RiskTriggerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Risk register query for the project's risks. Action-typed via {@code op}:
 * list / by_category / by_score / mitigation_status / get_details / triggers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskRegisterTool implements Tool {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final RiskRepository riskRepository;
    private final RiskCategoryMasterRepository categoryRepository;
    private final RiskTriggerRepository triggerRepository;
    private final RiskResponseRepository responseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "risk_register";
    }

    @Override
    public String description() {
        return "Use this when the user asks about project risks — the risk register, register entries, "
                + "RAG bands, mitigations, triggers, top risks. Supports operations via the `op` field: "
                + "`list` (filter by status/category/min-score, with RAG counts), `by_category` (group "
                + "counts per category master), `by_score` (top-N ranked by probability×impact / risk "
                + "score), `mitigation_status` (count by response_type / response status), `get_details` "
                + "(single risk + linked triggers + responses), `triggers` (list active and historical "
                + "triggers for project or for a single risk). Examples: \"show me the open risks\", "
                + "\"top 10 risks by score\", \"how many risks in the schedule category\", \"what's the "
                + "mitigation status across the register\", \"details for RISK-0042\", \"triggered "
                + "risks this month\". Project-scoped.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        ArrayNode opEnum = objectMapper.createArrayNode();
        opEnum.add("list");
        opEnum.add("by_category");
        opEnum.add("by_score");
        opEnum.add("mitigation_status");
        opEnum.add("get_details");
        opEnum.add("triggers");
        ObjectNode op = objectMapper.createObjectNode();
        op.put("type", "string");
        op.set("enum", opEnum);
        op.put("default", "list");
        op.put("description", "Operation to run. Default `list`.");
        props.set("op", op);
        props.set("status", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Filter by RiskStatus enum (e.g. IDENTIFIED, MITIGATING, RESOLVED). Used by `list`."));
        props.set("category_code", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Filter rows by RiskCategoryMaster.code. Used by `list`."));
        props.set("min_score", objectMapper.createObjectNode().put("type", "number")
                .put("description", "Filter to risks with riskScore >= this value. Used by `list`/`by_score`."));
        props.set("top_n", objectMapper.createObjectNode().put("type", "integer").put("default", 10)
                .put("description", "Used by `by_score` — top N risks by riskScore. Default 10."));
        props.set("risk_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
                .put("description", "Required for `get_details`; optional for `triggers` (filter to one risk)."));
        props.set("risk_code", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Resolves a single risk by code (e.g. RISK-0042). Used by `get_details`."));
        props.set("limit", objectMapper.createObjectNode().put("type", "integer")
                .put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT)
                .put("description", "Max rows for list-style ops."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("risk_register needs a project in scope. Pick a specific project, then re-ask.");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        String op = orDefault(input.path("op").asText(null), "list");
        int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

        return switch (op) {
            case "by_category" -> opByCategory(projectId);
            case "by_score" -> opByScore(projectId, input);
            case "mitigation_status" -> opMitigationStatus(projectId);
            case "get_details" -> opGetDetails(projectId, input);
            case "triggers" -> opTriggers(projectId, input, limit);
            default -> opList(projectId, input, limit);
        };
    }

    private ToolResult opList(UUID projectId, JsonNode input, int limit) {
        String statusStr = orNull(input.path("status").asText(null));
        String categoryCode = orNull(input.path("category_code").asText(null));
        Double minScore = input.path("min_score").isMissingNode() || input.path("min_score").isNull()
                ? null : input.path("min_score").asDouble();

        RiskStatus status = statusStr == null ? null : parseStatus(statusStr);
        List<Risk> all = status == null
                ? riskRepository.findByProjectId(projectId)
                : riskRepository.findByProjectIdAndStatus(projectId, status);

        List<Risk> filtered = new ArrayList<>();
        for (Risk r : all) {
            if (categoryCode != null && (r.getCategory() == null
                    || r.getCategory().getCode() == null
                    || !r.getCategory().getCode().equalsIgnoreCase(categoryCode))) continue;
            if (minScore != null && (r.getRiskScore() == null || r.getRiskScore() < minScore)) continue;
            filtered.add(r);
        }

        Map<String, Integer> ragCounts = new LinkedHashMap<>();
        for (RiskRag r : RiskRag.values()) ragCounts.put(r.name(), 0);
        for (Risk r : filtered) {
            if (r.getRag() != null) ragCounts.merge(r.getRag().name(), 1, Integer::sum);
        }
        int total = filtered.size();
        List<Risk> capped = filtered.size() > limit ? filtered.subList(0, limit) : filtered;

        ArrayNode rows = objectMapper.createArrayNode();
        for (Risk r : capped) rows.add(toRow(r));
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        ObjectNode summary = objectMapper.createObjectNode();
        for (Map.Entry<String, Integer> e : ragCounts.entrySet()) summary.put(e.getKey(), e.getValue());
        wrapper.set("rag_counts", summary);
        wrapper.put("matched", total);
        wrapper.put("returned", capped.size());
        return ToolResult.ok(String.format("%d risk%s on project (showing %d).",
                total, total == 1 ? "" : "s", capped.size()), wrapper);
    }

    private ToolResult opByCategory(UUID projectId) {
        List<Risk> all = riskRepository.findByProjectId(projectId);
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        Map<UUID, RiskCategoryMaster> known = new HashMap<>();
        int uncategorised = 0;
        for (Risk r : all) {
            if (r.getCategory() == null) {
                uncategorised++;
                continue;
            }
            UUID cid = r.getCategory().getId();
            counts.merge(cid, new int[]{1}, (a, b) -> new int[]{a[0] + b[0]});
            known.putIfAbsent(cid, r.getCategory());
        }
        ArrayNode rows = objectMapper.createArrayNode();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    RiskCategoryMaster cat = known.get(e.getKey());
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("category_id", e.getKey().toString());
                    n.put("category_code", cat == null ? null : cat.getCode());
                    n.put("category_name", cat == null ? null : cat.getName());
                    n.put("count", e.getValue()[0]);
                    rows.add(n);
                });
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("total_risks", all.size());
        wrapper.put("uncategorised", uncategorised);
        return ToolResult.ok(String.format("%d categories across %d risks", counts.size(), all.size()), wrapper);
    }

    private ToolResult opByScore(UUID projectId, JsonNode input) {
        int topN = Math.max(1, Math.min(MAX_LIMIT, input.path("top_n").asInt(10)));
        Double minScore = input.path("min_score").isMissingNode() || input.path("min_score").isNull()
                ? null : input.path("min_score").asDouble();
        List<Risk> sorted = riskRepository.findByProjectIdOrderByRiskScoreDesc(projectId);
        ArrayNode rows = objectMapper.createArrayNode();
        int kept = 0;
        for (Risk r : sorted) {
            if (kept >= topN) break;
            if (minScore != null && (r.getRiskScore() == null || r.getRiskScore() < minScore)) continue;
            rows.add(toRow(r));
            kept++;
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("returned", kept);
        wrapper.put("top_n", topN);
        return ToolResult.ok(String.format("Top %d risks by score (project total %d).", kept, sorted.size()), wrapper);
    }

    private ToolResult opMitigationStatus(UUID projectId) {
        List<Risk> all = riskRepository.findByProjectId(projectId);
        Map<String, Integer> byResponseType = new LinkedHashMap<>();
        int noStrategy = 0;
        for (Risk r : all) {
            if (r.getResponseType() == null) {
                noStrategy++;
            } else {
                byResponseType.merge(r.getResponseType().name(), 1, Integer::sum);
            }
        }
        // Pull responses for richer status counts
        List<UUID> riskIds = all.stream().map(Risk::getId).filter(java.util.Objects::nonNull).toList();
        List<RiskResponse> responses = riskIds.isEmpty() ? List.of() : responseRepository.findByRiskIdIn(riskIds);
        Map<String, Integer> responseStatus = new LinkedHashMap<>();
        for (RiskResponse rr : responses) {
            String s = rr.getStatus() == null ? "UNKNOWN" : rr.getStatus();
            responseStatus.merge(s, 1, Integer::sum);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode rt = objectMapper.createObjectNode();
        for (Map.Entry<String, Integer> e : byResponseType.entrySet()) rt.put(e.getKey(), e.getValue());
        wrapper.set("by_response_type", rt);
        wrapper.put("no_strategy", noStrategy);
        ObjectNode rs = objectMapper.createObjectNode();
        for (Map.Entry<String, Integer> e : responseStatus.entrySet()) rs.put(e.getKey(), e.getValue());
        wrapper.set("by_response_status", rs);
        wrapper.put("total_risks", all.size());
        wrapper.put("total_response_records", responses.size());
        return ToolResult.ok(String.format("Mitigation breakdown across %d risks (%d response records).",
                all.size(), responses.size()), wrapper);
    }

    private ToolResult opGetDetails(UUID projectId, JsonNode input) {
        Risk risk = resolveRisk(projectId, input);
        if (risk == null) {
            return ToolResult.error("Provide risk_id (UUID) or risk_code (e.g. RISK-0042) for get_details.");
        }
        List<RiskTrigger> triggers = triggerRepository.findByRiskId(risk.getId());
        List<RiskResponse> responses = responseRepository.findByRiskId(risk.getId());

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("risk", toRow(risk));
        ArrayNode trigRows = objectMapper.createArrayNode();
        for (RiskTrigger t : triggers) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("trigger_id", t.getId() == null ? null : t.getId().toString());
            n.put("condition", t.getTriggerCondition());
            n.put("trigger_type", t.getTriggerType() == null ? null : t.getTriggerType().name());
            n.put("threshold", t.getThresholdValue());
            n.put("current_value", t.getCurrentValue());
            n.put("is_triggered", t.getIsTriggered());
            n.put("escalation_level", t.getEscalationLevel() == null ? null : t.getEscalationLevel().name());
            n.put("triggered_at", t.getTriggeredAt() == null ? null : t.getTriggeredAt().toString());
            trigRows.add(n);
        }
        wrapper.set("triggers", trigRows);
        ArrayNode respRows = objectMapper.createArrayNode();
        for (RiskResponse r : responses) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("response_id", r.getId() == null ? null : r.getId().toString());
            n.put("response_type", r.getResponseType() == null ? null : r.getResponseType().name());
            n.put("description", r.getDescription());
            n.put("status", r.getStatus());
            n.put("planned_date", r.getPlannedDate() == null ? null : r.getPlannedDate().toString());
            n.put("actual_date", r.getActualDate() == null ? null : r.getActualDate().toString());
            n.put("estimated_cost", r.getEstimatedCost() == null ? null : r.getEstimatedCost().doubleValue());
            n.put("actual_cost", r.getActualCost() == null ? null : r.getActualCost().doubleValue());
            respRows.add(n);
        }
        wrapper.set("responses", respRows);

        Map<String, List<UUID>> links = new HashMap<>();
        if (!triggers.isEmpty()) {
            links.put("trigger", triggers.stream().map(RiskTrigger::getId)
                    .filter(java.util.Objects::nonNull).toList());
        }
        if (!responses.isEmpty()) {
            links.put("response", responses.stream().map(RiskResponse::getId)
                    .filter(java.util.Objects::nonNull).toList());
        }
        ToolResult.attachLinks(wrapper, links);

        return ToolResult.ok(risk.getCode() + " — " + risk.getTitle()
                + " · " + (risk.getStatus() == null ? "?" : risk.getStatus().name())
                + " · " + triggers.size() + " trigger" + (triggers.size() == 1 ? "" : "s")
                + ", " + responses.size() + " response" + (responses.size() == 1 ? "" : "s"), wrapper);
    }

    private ToolResult opTriggers(UUID projectId, JsonNode input, int limit) {
        String idStr = orNull(input.path("risk_id").asText(null));
        UUID riskId = null;
        if (idStr != null) {
            try { riskId = UUID.fromString(idStr); } catch (IllegalArgumentException ignored) {}
        }
        List<RiskTrigger> all = riskId != null
                ? triggerRepository.findByRiskId(riskId)
                : triggerRepository.findByProjectId(projectId);
        List<RiskTrigger> capped = all.size() > limit ? all.subList(0, limit) : all;
        int triggeredCount = 0;
        ArrayNode rows = objectMapper.createArrayNode();
        for (RiskTrigger t : capped) {
            if (Boolean.TRUE.equals(t.getIsTriggered())) triggeredCount++;
            ObjectNode n = objectMapper.createObjectNode();
            n.put("trigger_id", t.getId() == null ? null : t.getId().toString());
            n.put("risk_id", t.getRiskId() == null ? null : t.getRiskId().toString());
            n.put("condition", t.getTriggerCondition());
            n.put("trigger_type", t.getTriggerType() == null ? null : t.getTriggerType().name());
            n.put("threshold", t.getThresholdValue());
            n.put("current_value", t.getCurrentValue());
            n.put("is_triggered", t.getIsTriggered());
            n.put("escalation_level", t.getEscalationLevel() == null ? null : t.getEscalationLevel().name());
            rows.add(n);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("matched", all.size());
        wrapper.put("returned", capped.size());
        wrapper.put("triggered_count", triggeredCount);
        return ToolResult.ok(String.format("%d trigger%s%s (%d active).",
                all.size(), all.size() == 1 ? "" : "s",
                riskId != null ? " for risk " + riskId : "",
                triggeredCount), wrapper);
    }

    private ObjectNode toRow(Risk r) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("risk_id", r.getId() == null ? null : r.getId().toString());
        n.put("code", r.getCode());
        n.put("title", r.getTitle());
        n.put("status", r.getStatus() == null ? null : r.getStatus().name());
        n.put("risk_type", r.getRiskType() == null ? null : r.getRiskType().name());
        n.put("probability", r.getProbability() == null ? null : r.getProbability().name());
        n.put("impact_cost", r.getImpactCost());
        n.put("impact_schedule", r.getImpactSchedule());
        n.put("risk_score", r.getRiskScore());
        n.put("residual_risk_score", r.getResidualRiskScore());
        n.put("rag", r.getRag() == null ? null : r.getRag().name());
        n.put("trend", r.getTrend() == null ? null : r.getTrend().name());
        n.put("response_type", r.getResponseType() == null ? null : r.getResponseType().name());
        n.put("owner_id", r.getOwnerId() == null ? null : r.getOwnerId().toString());
        n.put("category_id", r.getCategory() == null ? null : r.getCategory().getId().toString());
        n.put("category_code", r.getCategory() == null ? null : r.getCategory().getCode());
        n.put("category_name", r.getCategory() == null ? null : r.getCategory().getName());
        n.put("identified_date", r.getIdentifiedDate() == null ? null : r.getIdentifiedDate().toString());
        n.put("due_date", r.getDueDate() == null ? null : r.getDueDate().toString());
        n.put("cost_impact", r.getCostImpact() == null ? null : r.getCostImpact().doubleValue());
        n.put("schedule_impact_days", r.getScheduleImpactDays());
        return n;
    }

    private Risk resolveRisk(UUID projectId, JsonNode input) {
        String idStr = orNull(input.path("risk_id").asText(null));
        if (idStr != null) {
            try {
                UUID id = UUID.fromString(idStr);
                Optional<Risk> opt = riskRepository.findById(id);
                if (opt.isPresent() && projectId.equals(opt.get().getProjectId())) return opt.get();
            } catch (IllegalArgumentException ignored) {}
        }
        String code = orNull(input.path("risk_code").asText(null));
        if (code != null) {
            return riskRepository.findByProjectId(projectId).stream()
                    .filter(r -> code.equalsIgnoreCase(r.getCode()))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private static RiskStatus parseStatus(String s) {
        try { return RiskStatus.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private static String orNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String orDefault(String s, String d) {
        return s == null || s.isBlank() ? d : s.trim();
    }
}
