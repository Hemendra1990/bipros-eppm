package com.bipros.ai.tool.supervisor;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.query.SupervisorRosterCalculator;
import com.bipros.ai.query.SupervisorRosterCalculator.RankBy;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRoster;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRow;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Discovery tool for supervisors on a project. Returns one row per supervisor
 * with activity count, status breakdown, lite cost / EVM, and a flag for
 * pool-only rows when {@code include_eligible_pool=true}.
 *
 * <p>Use this <em>before</em> {@code supervisor} (single-supervisor drill-down)
 * or {@code compare_supervisors} (multi-supervisor side-by-side) when the
 * user's question doesn't name a supervisor — "how many supervisors are
 * there?", "list supervisors on project X", "rank supervisors by cost", etc.
 *
 * <p>Visible to every profile (empty {@link #allowedRoles()}) — there's no
 * cost-sensitive data in the roster and the per-row planned/actual cost
 * already obeys the project-scope access check on the underlying activities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListSupervisorsTool implements Tool {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final SupervisorRosterCalculator rosterCalculator;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "list_supervisors";
  }

  @Override
  public String description() {
    return "List supervisors on a project with per-supervisor activity counts, status "
        + "breakdown (not_started / in_progress / completed), lite cost (planned, actual, "
        + "variance %) and EVM (CPI, SPI), plus an optional ranking. Use this BEFORE the "
        + "`supervisor` tool (single-supervisor drill-down) or `compare_supervisors` "
        + "(multi-supervisor side-by-side) when the user asks discovery questions like "
        + "\"how many supervisors are there?\", \"list supervisors on this project\", "
        + "\"who supervises project X?\", or \"rank supervisors by activity count / cost / "
        + "CPI\". Defaults to actively-assigned supervisors (i.e. anyone who is the "
        + "responsibleResource on at least one activity); set include_eligible_pool=true to "
        + "also surface unassigned LABOR-type resources in the project's pool. "
        + "Project-scoped — requires project_id or current project scope.";
  }

  @Override
  public Set<String> allowedRoles() {
    return Collections.emptySet();
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();

    props.set(
        "project_id",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("format", "uuid")
            .put(
                "description",
                "Project UUID. Optional when the conversation already has a current "
                    + "project in scope; required in portfolio mode."));

    props.set(
        "include_eligible_pool",
        objectMapper
            .createObjectNode()
            .put("type", "boolean")
            .put("default", false)
            .put(
                "description",
                "When true, also returns LABOR-type resources in the project's pool "
                    + "that have zero assigned activities. Pool rows are flagged "
                    + "is_in_pool=true."));

    ArrayNode rankEnum = objectMapper.createArrayNode();
    rankEnum.add("activity_count");
    rankEnum.add("planned_cost");
    rankEnum.add("actual_cost");
    rankEnum.add("cpi");
    rankEnum.add("spi");
    rankEnum.add("avg_percent_complete");
    ObjectNode rank = objectMapper.createObjectNode();
    rank.put("type", "string");
    rank.set("enum", rankEnum);
    rank.put("default", "activity_count");
    rank.put(
        "description",
        "Sort axis. All ranks descend (higher first); nulls go last. Default: activity_count.");
    props.set("rank_by", rank);

    ObjectNode limit = objectMapper.createObjectNode();
    limit.put("type", "integer");
    limit.put("default", DEFAULT_LIMIT);
    limit.put("minimum", 1);
    limit.put("maximum", MAX_LIMIT);
    limit.put("description", "Max rows returned (clamped 1.." + MAX_LIMIT + ").");
    props.set("limit", limit);

    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = resolveProjectId(input, ctx);
    if (projectId == null) {
      if ("ADMIN".equals(ctx.role())) {
        return ToolResult.error(
            "list_supervisors needs a project_id when called in portfolio mode — "
                + "call list_projects first then retry with one of the returned UUIDs.");
      }
      return ToolResult.error(
          "list_supervisors needs a project in scope. Provide project_id or open a project first.");
    }

    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    boolean includePool = input.path("include_eligible_pool").asBoolean(false);
    RankBy rankBy = parseRankBy(input.path("rank_by").asText(null));
    int limit = clamp(input.path("limit").asInt(DEFAULT_LIMIT), 1, MAX_LIMIT);

    SupervisorRoster roster = rosterCalculator.compute(projectId, includePool, rankBy, limit);

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("project_id", roster.projectId().toString());
    if (roster.projectCode() != null) wrapper.put("project_code", roster.projectCode());
    if (roster.projectName() != null) wrapper.put("project_name", roster.projectName());
    wrapper.put("total_supervisors", roster.totalSupervisors());
    wrapper.put("returned", roster.rows().size());
    wrapper.put("include_eligible_pool", includePool);
    wrapper.put("ranked_by", rankBy.name().toLowerCase(Locale.ROOT));

    ArrayNode rowsNode = objectMapper.createArrayNode();
    List<UUID> linkedIds = new ArrayList<>();
    for (SupervisorRow r : roster.rows()) {
      rowsNode.add(renderRow(r));
      if (r.supervisorResourceId() != null && !r.isInPool()) {
        linkedIds.add(r.supervisorResourceId());
      }
    }
    wrapper.set("rows", rowsNode);

    if (!linkedIds.isEmpty()) {
      ToolResult.attachLinks(wrapper, Map.of("supervisors", linkedIds));
    }

    String summary = buildSummary(roster, includePool);
    return ToolResult.ok(summary, wrapper);
  }

  private ObjectNode renderRow(SupervisorRow r) {
    ObjectNode n = objectMapper.createObjectNode();
    if (r.supervisorResourceId() != null) {
      n.put("supervisor_resource_id", r.supervisorResourceId().toString());
    }
    n.put("code", r.code());
    n.put("name", r.name());
    n.put("role_name", r.roleName());
    n.put("activity_count", r.activityCount());

    ObjectNode status = objectMapper.createObjectNode();
    status.put("not_started", r.statusBreakdown().notStarted());
    status.put("in_progress", r.statusBreakdown().inProgress());
    status.put("completed", r.statusBreakdown().completed());
    status.put("on_hold", r.statusBreakdown().onHold());
    n.set("status_breakdown", status);

    if (r.avgPercentComplete() != null) {
      n.put("avg_percent_complete", r.avgPercentComplete().toPlainString());
    }
    if (r.plannedCost() != null) n.put("planned_cost", r.plannedCost().toPlainString());
    if (r.actualCost() != null) n.put("actual_cost", r.actualCost().toPlainString());
    if (r.costVariancePct() != null) {
      n.put("cost_variance_pct", r.costVariancePct().toPlainString());
    }
    if (r.cpi() != null) n.put("cpi", r.cpi().toPlainString());
    if (r.spi() != null) n.put("spi", r.spi().toPlainString());
    n.put("is_in_pool", r.isInPool());
    return n;
  }

  private static String buildSummary(SupervisorRoster roster, boolean includePool) {
    int assigned = 0;
    int pool = 0;
    for (SupervisorRow r : roster.rows()) {
      if (r.isInPool()) pool++;
      else assigned++;
    }
    StringBuilder sb = new StringBuilder();
    if (roster.projectName() != null) {
      sb.append(roster.projectName()).append(" — ");
    }
    sb.append(assigned).append(" assigned supervisor");
    if (assigned != 1) sb.append('s');
    if (includePool) {
      sb.append(", ").append(pool).append(" in eligible pool");
    }
    if (!roster.rows().isEmpty()) {
      SupervisorRow top = roster.rows().get(0);
      if (top.name() != null && !top.isInPool()) {
        sb.append(". Top: ").append(top.name())
            .append(" (").append(top.activityCount()).append(" activit");
        sb.append(top.activityCount() == 1 ? "y" : "ies");
        if (top.cpi() != null) sb.append(", CPI ").append(formatScalar(top.cpi()));
        sb.append(")");
      }
    }
    return sb.toString();
  }

  private static String formatScalar(BigDecimal v) {
    if (v == null) return "n/a";
    return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private UUID resolveProjectId(JsonNode input, AiContext ctx) {
    String raw = input.path("project_id").asText(null);
    if (raw != null && !raw.isBlank()) {
      try {
        return UUID.fromString(raw.trim());
      } catch (IllegalArgumentException e) {
        log.debug("list_supervisors received malformed project_id={}", raw);
      }
    }
    return ctx.projectId();
  }

  private static RankBy parseRankBy(String raw) {
    if (raw == null || raw.isBlank()) return RankBy.ACTIVITY_COUNT;
    try {
      return RankBy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return RankBy.ACTIVITY_COUNT;
    }
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
