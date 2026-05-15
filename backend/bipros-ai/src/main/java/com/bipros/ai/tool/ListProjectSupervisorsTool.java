package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Canonical "supervisors on this project" lookup for the role-rate model. UNIONs both surfaces
 * where a supervisor identity surfaces in the new model:
 *
 * <ul>
 *   <li>{@code activity.activities.supervisor_user_id} — the assigned supervisor for an activity
 *       (visible on the activity sidebar). Present even when no DPR has been filed yet.</li>
 *   <li>{@code project.daily_progress_reports.supervisor_user_id} — who actually filed a DPR.
 *       Optional date window narrows this side only; activity assignments are always returned.</li>
 * </ul>
 *
 * <p>Each returned row carries both {@code activity_count} and {@code dpr_count}, plus a
 * {@code sources} array tagged with {@code ACTIVITY} and/or {@code DPR}. This is the only AI
 * tool that returns User UUIDs matching {@code supervisor_user_id} in the new model —
 * {@code list_supervisors} / {@code resolve_entity(kind='supervisor')} both speak the legacy
 * Resource/ManpowerMaster schema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListProjectSupervisorsTool implements Tool {

    @PersistenceContext private EntityManager em;

    private final ObjectMapper mapper;

    @Override
    public String name() {
        return "list_project_supervisors";
    }

    @Override
    public String description() {
        return "Distinct User-FK supervisors on the current project — UNIONed across BOTH the "
                + "activity-assigned surface (activity.activities.supervisor_user_id, what the "
                + "activity sidebar shows) AND the DPR-submitted surface "
                + "(daily_progress_reports.supervisor_user_id, who actually filed reports). "
                + "Returns one row per User UUID with: supervisor_user_id, supervisor_code "
                + "(username, e.g. 'EMP-001'), supervisor_name (full name from public.users), "
                + "activity_count (# of activities they're assigned supervisor of), "
                + "dpr_count (# of DPRs they filed in the optional window), and sources array "
                + "tagged with 'ACTIVITY' and/or 'DPR'. "
                + "ALWAYS call this for any 'who are the supervisors on project X', 'list "
                + "supervisors', 'distinct supervisors across activities', or 'supervisors "
                + "available' question — it is the canonical roster for the role-rate model. "
                + "Also call it first when the user names a supervisor in prose and you need a "
                + "User UUID (optionally use name_filter to narrow). "
                + "Do NOT use list_supervisors (legacy responsibleResourceId, returns 0) or "
                + "resolve_entity(kind='supervisor') (legacy Resource UUIDs that won't match) "
                + "for these questions. Optional from_date / to_date narrow the DPR side only — "
                + "an activity supervisor with zero DPRs in the window still appears with "
                + "dpr_count=0 and sources=['ACTIVITY'].";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = mapper.createObjectNode();
        addStringProp(props, "from_date",
                "ISO date (yyyy-MM-dd) — narrows the DPR-side count only. Activity-assigned "
                        + "supervisors are always returned regardless of date.");
        addStringProp(props, "to_date",
                "ISO date (yyyy-MM-dd) — narrows the DPR-side count only.");
        addStringProp(props, "name_filter",
                "Optional case-insensitive substring filter on username + display name. Use "
                        + "this when the user named a single supervisor in prose.");
        schema.set("properties", props);
        schema.set("required", mapper.createArrayNode());
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("list_project_supervisors requires a project in scope.");
        }
        LocalDate fromDate = parseDate(input, "from_date");
        LocalDate toDate = parseDate(input, "to_date");
        String nameFilter = text(input, "name_filter");
        String normalisedFilter = nameFilter == null
                ? null : nameFilter.toLowerCase(Locale.ROOT);

        Query q = em.createNativeQuery(
                "WITH activity_sups AS ("
                        + "  SELECT a.supervisor_user_id AS user_id, "
                        + "         COUNT(*)            AS activity_count "
                        + "    FROM activity.activities a "
                        + "   WHERE a.project_id = :projectId "
                        + "     AND a.supervisor_user_id IS NOT NULL "
                        + "   GROUP BY a.supervisor_user_id "
                        + "), "
                        + "dpr_sups AS ("
                        + "  SELECT d.supervisor_user_id AS user_id, "
                        + "         MAX(d.supervisor_name) AS supervisor_name_snapshot, "
                        + "         COUNT(*)            AS dpr_count "
                        + "    FROM project.daily_progress_reports d "
                        + "   WHERE d.project_id = :projectId "
                        + "     AND d.supervisor_user_id IS NOT NULL "
                        + "     AND (CAST(:fromDate AS date) IS NULL OR d.report_date >= CAST(:fromDate AS date)) "
                        + "     AND (CAST(:toDate AS date) IS NULL OR d.report_date <= CAST(:toDate AS date)) "
                        + "   GROUP BY d.supervisor_user_id "
                        + ") "
                        + "SELECT COALESCE(a.user_id, d.user_id) AS user_id, "
                        + "       COALESCE(u.username, '') AS supervisor_code, "
                        + "       COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), "
                        + "                d.supervisor_name_snapshot, "
                        + "                u.username, "
                        + "                '') AS supervisor_name, "
                        + "       COALESCE(a.activity_count, 0) AS activity_count, "
                        + "       COALESCE(d.dpr_count, 0)       AS dpr_count, "
                        + "       (a.user_id IS NOT NULL)        AS from_activity, "
                        + "       (d.user_id IS NOT NULL)        AS from_dpr "
                        + "  FROM activity_sups a "
                        + "  FULL OUTER JOIN dpr_sups d ON d.user_id = a.user_id "
                        + "  LEFT JOIN public.users u "
                        + "    ON u.id = COALESCE(a.user_id, d.user_id) "
                        + " ORDER BY activity_count DESC, dpr_count DESC, supervisor_name");
        q.setParameter("projectId", projectId);
        q.setParameter("fromDate", fromDate);
        q.setParameter("toDate", toDate);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("project_id", projectId.toString());
        if (fromDate != null) wrapper.put("from_date", fromDate.toString());
        if (toDate != null) wrapper.put("to_date", toDate.toString());
        if (nameFilter != null) wrapper.put("name_filter", nameFilter);

        ArrayNode arr = mapper.createArrayNode();
        int activityOnly = 0;
        int dprOnly = 0;
        int both = 0;
        for (Object[] r : rows) {
            UUID userId = (UUID) r[0];
            String code = r[1] != null ? r[1].toString() : null;
            String name = r[2] != null ? r[2].toString() : null;
            long activityCount = ((Number) r[3]).longValue();
            long dprCount = ((Number) r[4]).longValue();
            boolean fromActivity = toBool(r[5]);
            boolean fromDpr = toBool(r[6]);

            if (normalisedFilter != null) {
                String codeL = code == null ? "" : code.toLowerCase(Locale.ROOT);
                String nameL = name == null ? "" : name.toLowerCase(Locale.ROOT);
                if (!codeL.contains(normalisedFilter) && !nameL.contains(normalisedFilter)) {
                    continue;
                }
            }

            ObjectNode row = mapper.createObjectNode();
            row.put("supervisor_user_id", userId != null ? userId.toString() : null);
            row.put("supervisor_code", code);
            row.put("supervisor_name", name);
            row.put("activity_count", activityCount);
            row.put("dpr_count", dprCount);
            ArrayNode sources = mapper.createArrayNode();
            if (fromActivity) sources.add("ACTIVITY");
            if (fromDpr) sources.add("DPR");
            row.set("sources", sources);
            arr.add(row);

            if (fromActivity && fromDpr) both++;
            else if (fromActivity) activityOnly++;
            else if (fromDpr) dprOnly++;
        }
        wrapper.set("supervisors", arr);
        wrapper.put("count", arr.size());
        ObjectNode breakdown = mapper.createObjectNode();
        breakdown.put("both_activity_and_dpr", both);
        breakdown.put("activity_only", activityOnly);
        breakdown.put("dpr_only", dprOnly);
        wrapper.set("source_breakdown", breakdown);

        String summary;
        if (arr.size() == 0) {
            summary = nameFilter != null
                    ? "No supervisors match \"" + nameFilter + "\" on this project."
                    : "No supervisors found on this project (no activity assignments and no DPRs).";
        } else if (arr.size() == 1) {
            ObjectNode only = (ObjectNode) arr.get(0);
            summary = "1 supervisor: " + only.path("supervisor_name").asText()
                    + " (" + only.path("supervisor_code").asText() + ", "
                    + only.path("activity_count").asLong() + " activit"
                    + (only.path("activity_count").asLong() == 1 ? "y" : "ies") + ", "
                    + only.path("dpr_count").asLong() + " DPR"
                    + (only.path("dpr_count").asLong() == 1 ? "" : "s") + ").";
        } else {
            summary = arr.size() + " distinct supervisors on project (" + both
                    + " with both activity assignments and DPRs, " + activityOnly
                    + " activity-only, " + dprOnly + " DPR-only).";
        }
        return ToolResult.ok(summary, wrapper);
    }

    private static boolean toBool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        String s = o.toString();
        return "true".equalsIgnoreCase(s) || "t".equalsIgnoreCase(s) || "1".equals(s);
    }

    private void addStringProp(ObjectNode props, String name, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "string");
        n.put("description", description);
        props.set(name, n);
    }

    private static String text(JsonNode in, String field) {
        JsonNode n = in == null ? null : in.path(field);
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText(null);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate parseDate(JsonNode in, String field) {
        String s = text(in, field);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
