package com.bipros.api.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.api.dprreport.DprAgentReport;
import com.bipros.api.dprreport.DprReportConfig;
import com.bipros.api.dprreport.DprReportWindow;
import com.bipros.api.dprreport.ReportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs a fresh, on-demand DPR + capacity analysis for a window/filters, saves it as a
 * {@link DprAgentReport} row (so it can later be resent via {@link EmailDprReportTool}),
 * and returns its headline numbers to the chat. Reuses the exact same
 * {@code com.bipros.api.dprreport.DprReportService} pipeline (snapshot → metrics → LLM
 * insights → verifier → HTML) that the scheduled agent uses — the DPR Analyst never
 * recomputes numbers itself.
 *
 * <p>Always calls {@code generate} with trigger {@code ON_DEMAND}, empty email
 * recipients, and {@code deliverInApp=false} — this is preview-only and never spams an
 * email or in-app notification. Use {@link EmailDprReportTool} separately, with explicit
 * user confirmation, to actually send the saved report.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeDprTool implements Tool {

    private final com.bipros.api.dprreport.DprReportService dprReportService;
    private final com.bipros.api.dprreport.DprAgentReportRepository reportRepository;
    private final ObjectMapper mapper;

    /**
     * De-dupes an identical analyze (same project + window + filters) requested again within a
     * short window — e.g. the orchestrator's verification round re-calling this tool — so it
     * returns the just-created report instead of persisting a duplicate row (which would clutter
     * the "which report?" list) and re-running the whole LLM pipeline.
     */
    private record RecentGen(UUID reportId, long at) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, RecentGen> RECENT_GENERATES =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEDUPE_WINDOW_MS = 120_000L;

    @Override
    public String name() {
        return "analyze_dpr";
    }

    @Override
    public String description() {
        return "Run a fresh DPR + capacity analysis for a window/filters, save it as a "
                + "report, and return its highlights. Use before emailing when no report "
                + "exists yet, or when the user wants a fresh look (a different date window, "
                + "supervisor, activity, or BOQ item filter) rather than one of the reports "
                + "returned by list_dpr_reports. This call is preview-only — it does not "
                + "email or notify anyone. Full charts live on the saved report; use "
                + "email_dpr_report (with user confirmation) to send it on. Requires a "
                + "project in scope.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = mapper.createObjectNode();

        props.set("from_date", strSchema(
                "ISO date (yyyy-MM-dd). Combine with to_date for a custom window; "
                        + "overrides preset when both are given."));
        props.set("to_date", strSchema(
                "ISO date (yyyy-MM-dd). Combine with from_date for a custom window; "
                        + "overrides preset when both are given."));

        ArrayNode presetEnum = mapper.createArrayNode();
        presetEnum.add("LAST_1_DAY");
        presetEnum.add("LAST_7_DAYS");
        presetEnum.add("LAST_30_DAYS");
        presetEnum.add("THIS_MONTH");
        presetEnum.add("PROJECT_TO_DATE");
        ObjectNode preset = mapper.createObjectNode();
        preset.put("type", "string");
        preset.set("enum", presetEnum);
        preset.put("description",
                "Preset window. Ignored when from_date+to_date are both given. Default: LAST_7_DAYS.");
        props.set("preset", preset);

        props.set("supervisor_user_ids", uuidArraySchema(
                "Restrict analysis to these supervisor user UUIDs. Omit for all supervisors."));
        props.set("activity_ids", uuidArraySchema(
                "Restrict analysis to these activity UUIDs. Omit for all activities."));
        props.set("boq_item_ids", uuidArraySchema(
                "Restrict analysis to these BOQ item UUIDs. Omit for all BOQ items."));

        schema.set("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("analyze_dpr requires a project in scope.");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        try {
            LocalDate from = parseDateOrNull(input, "from_date");
            LocalDate to = parseDateOrNull(input, "to_date");
            DprReportWindow win = (from != null && to != null)
                    ? DprReportWindow.ofCustom(from, to)
                    : DprReportWindow.ofPreset(parsePreset(text(input, "preset")), LocalDate.now(), null);

            List<UUID> supIds = parseUuidArray(input, "supervisor_user_ids");
            List<UUID> actIds = parseUuidArray(input, "activity_ids");
            List<UUID> boqIds = parseUuidArray(input, "boq_item_ids");

            ReportRequest req = new ReportRequest(
                    projectId, win.from(), win.to(), win.label(),
                    supIds, actIds, boqIds,
                    "ON_DEMAND", ctx.userId(), List.of(), false);

            long now = System.currentTimeMillis();
            RECENT_GENERATES.values().removeIf(g -> now - g.at() > DEDUPE_WINDOW_MS);
            String key = projectId + "|" + win.from() + "|" + win.to() + "|"
                    + idKey(supIds) + "|" + idKey(actIds) + "|" + idKey(boqIds);
            RecentGen recent = RECENT_GENERATES.get(key);
            if (recent != null && now - recent.at() < DEDUPE_WINDOW_MS) {
                var cached = reportRepository.findById(recent.reportId());
                if (cached.isPresent()) {
                    return ToolResult.ok(buildSummary(cached.get()), renderResponse(cached.get()));
                }
            }

            DprAgentReport report = dprReportService.generate(req);
            if (report.getId() != null) RECENT_GENERATES.put(key, new RecentGen(report.getId(), now));
            return ToolResult.ok(buildSummary(report), renderResponse(report));
        } catch (Exception e) {
            log.warn("analyze_dpr failed", e);
            return ToolResult.error("Failed to analyze DPR: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────── rendering

    private ObjectNode renderResponse(DprAgentReport report) {
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("report_id", report.getId() == null ? null : report.getId().toString());
        wrapper.put("window_label", report.getWindowLabel());
        wrapper.put("status", report.getStatus());
        wrapper.put("summary", summaryOf(report));
        wrapper.set("highlights", renderHighlights(report));
        return wrapper;
    }

    private ArrayNode renderHighlights(DprAgentReport report) {
        ArrayNode highlights = mapper.createArrayNode();
        String insightsJson = report.getInsightsJson();
        if (insightsJson == null || insightsJson.isBlank()) return highlights;
        try {
            JsonNode insights = mapper.readTree(insightsJson);
            JsonNode arr = insights.get("highlights");
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    ObjectNode row = mapper.createObjectNode();
                    row.put("label", item.path("label").asText(null));
                    row.put("value", item.path("value").asText(null));
                    highlights.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("analyze_dpr: failed to parse insights_json for report {}", report.getId(), e);
        }
        return highlights;
    }

    private String summaryOf(DprAgentReport report) {
        String s = report.getSummary();
        return (s == null || s.isBlank()) ? report.getWindowLabel() : s;
    }

    private String buildSummary(DprAgentReport report) {
        return "DPR analysis (" + report.getWindowLabel() + ") — " + report.getStatus()
                + ": " + summaryOf(report);
    }

    // ──────────────────────────────────────────────────────── helpers

    private DprReportConfig.WindowPreset parsePreset(String raw) {
        if (raw == null || raw.isBlank()) return DprReportConfig.WindowPreset.LAST_7_DAYS;
        try {
            return DprReportConfig.WindowPreset.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return DprReportConfig.WindowPreset.LAST_7_DAYS;
        }
    }

    private List<UUID> parseUuidArray(JsonNode input, String field) {
        List<UUID> out = new ArrayList<>();
        JsonNode arr = input.path(field);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                String s = n.asText(null);
                if (s == null || s.isBlank()) continue;
                try {
                    out.add(UUID.fromString(s.trim()));
                } catch (Exception ignored) {
                    // skip malformed ids rather than failing the whole call
                }
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

    private static LocalDate parseDateOrNull(JsonNode in, String field) {
        String s = text(in, field);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Order-independent key fragment for a filter id list (empty for null/empty). */
    private static String idKey(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private ObjectNode strSchema(String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "string");
        n.put("description", description);
        return n;
    }

    private ObjectNode uuidArraySchema(String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "array");
        n.put("description", description);
        n.set("items", mapper.createObjectNode().put("type", "string").put("format", "uuid"));
        return n;
    }
}
