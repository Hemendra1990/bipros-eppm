package com.bipros.ai.tool.plan;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.NextDayPlan;
import com.bipros.project.domain.repository.NextDayPlanRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supervisor Daily Report Section D — next-day plans (committed activities for
 * the upcoming work day). Action-typed via {@code op}: {@code list},
 * {@code by_supervisor} (matches on {@code actionBy}, the assignee text),
 * {@code by_activity} (substring on the free-text {@code nextDayActivity}),
 * {@code by_date} (single {@code report_date}). Activities are stored as plain
 * text on this row so we don't join Activity master here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NextDayPlanTool implements Tool {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final NextDayPlanRepository nextDayPlanRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "next_day_plan";
  }

  @Override
  public String description() {
    return "Use this when the user asks about next-day plans / tomorrow's commitments / Section D "
        + "of the supervisor daily report — \"what's planned for tomorrow\", \"who's tasked with "
        + "what next\", \"plans for activity X\", \"plans on April 18\". One tool, multiple ops "
        + "via the `op` param: `list` (project rows, paginated), `by_supervisor` (substring on "
        + "actionBy / assignee text), `by_activity` (substring on the free-text nextDayActivity), "
        + "`by_date` (rows for a single report_date). For what was actually executed instead of "
        + "planned, use query_dpr. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();

    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("list");
    opEnum.add("by_supervisor");
    opEnum.add("by_activity");
    opEnum.add("by_date");
    ObjectNode op = objectMapper.createObjectNode();
    op.put("type", "string");
    op.set("enum", opEnum);
    op.put("description", "Which sub-query to run. Required.");
    props.set("op", op);

    props.set("supervisor", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Substring (case-insensitive) on actionBy. Required by `by_supervisor`."));
    props.set("activity", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Substring (case-insensitive) on nextDayActivity. Required by `by_activity`."));
    props.set("report_date", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Required by `by_date`."));
    props.set("date_from", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Optional window start used by `list` / `by_supervisor` / `by_activity`."));
    props.set("date_to", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Optional window end (defaults to today when date_from is set)."));
    props.set("limit", objectMapper.createObjectNode()
        .put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT));

    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) {
      return ToolResult.error("next_day_plan needs a project in scope. Pick a project, then re-ask.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = orNull(input.path("op").asText(null));
    if (op == null) {
      return ToolResult.error("next_day_plan requires `op` ∈ {list, by_supervisor, by_activity, by_date}.");
    }
    int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

    return switch (op.toLowerCase()) {
      case "list" -> doList(input, projectId, limit, null, null);
      case "by_supervisor" -> doSupervisor(input, projectId, limit);
      case "by_activity" -> doActivity(input, projectId, limit);
      case "by_date" -> doByDate(input, projectId, limit);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doList(JsonNode input, UUID projectId, int limit,
                            String supervisorFilter, String activityFilter) {
    LocalDate from = parseDate(input.path("date_from").asText(null), null);
    LocalDate to = parseDate(input.path("date_to").asText(null), null);
    if (from != null && to == null) to = LocalDate.now();
    if (from != null && from.isAfter(to)) { LocalDate t = from; from = to; to = t; }

    List<NextDayPlan> base;
    if (from != null) {
      base = nextDayPlanRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
          projectId, from, to);
    } else {
      base = nextDayPlanRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);
    }
    List<NextDayPlan> filtered = applyTextFilters(base, supervisorFilter, activityFilter);
    int matched = filtered.size();
    if (filtered.size() > limit) filtered = filtered.subList(0, limit);

    ObjectNode wrapper = build(filtered);
    wrapper.put("matched", matched);
    wrapper.put("returned", filtered.size());
    if (from != null) wrapper.put("date_from", from.toString());
    if (to != null) wrapper.put("date_to", to.toString());
    if (supervisorFilter != null) wrapper.put("supervisor_filter", supervisorFilter);
    if (activityFilter != null) wrapper.put("activity_filter", activityFilter);
    return ToolResult.ok(String.format("%d next-day plan row%s.",
        matched, matched == 1 ? "" : "s"), wrapper);
  }

  private ToolResult doSupervisor(JsonNode input, UUID projectId, int limit) {
    String s = orNull(input.path("supervisor").asText(null));
    if (s == null) return ToolResult.error("by_supervisor requires `supervisor` (substring).");
    return doList(input, projectId, limit, s, null);
  }

  private ToolResult doActivity(JsonNode input, UUID projectId, int limit) {
    String a = orNull(input.path("activity").asText(null));
    if (a == null) return ToolResult.error("by_activity requires `activity` (substring).");
    return doList(input, projectId, limit, null, a);
  }

  private ToolResult doByDate(JsonNode input, UUID projectId, int limit) {
    LocalDate d = parseDate(input.path("report_date").asText(null), null);
    if (d == null) return ToolResult.error("by_date requires `report_date` (ISO date).");
    List<NextDayPlan> base = nextDayPlanRepository
        .findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, d, d);
    int matched = base.size();
    if (base.size() > limit) base = base.subList(0, limit);
    ObjectNode wrapper = build(base);
    wrapper.put("report_date", d.toString());
    wrapper.put("matched", matched);
    wrapper.put("returned", base.size());
    return ToolResult.ok(String.format("%d next-day plan row%s on %s.",
        matched, matched == 1 ? "" : "s", d), wrapper);
  }

  private List<NextDayPlan> applyTextFilters(List<NextDayPlan> rows,
                                             String supervisorFilter, String activityFilter) {
    if (supervisorFilter == null && activityFilter == null) return rows;
    String supLc = supervisorFilter == null ? null : supervisorFilter.toLowerCase();
    String actLc = activityFilter == null ? null : activityFilter.toLowerCase();
    List<NextDayPlan> out = new ArrayList<>();
    for (NextDayPlan p : rows) {
      if (supLc != null && (p.getActionBy() == null || !p.getActionBy().toLowerCase().contains(supLc))) continue;
      if (actLc != null && (p.getNextDayActivity() == null
          || !p.getNextDayActivity().toLowerCase().contains(actLc))) continue;
      out.add(p);
    }
    return out;
  }

  private ObjectNode build(List<NextDayPlan> rows) {
    ArrayNode arr = objectMapper.createArrayNode();
    BigDecimal totalTarget = BigDecimal.ZERO;
    for (NextDayPlan p : rows) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("plan_id", p.getId() == null ? null : p.getId().toString());
      n.put("report_date", p.getReportDate() == null ? null : p.getReportDate().toString());
      n.put("next_day_activity", p.getNextDayActivity());
      n.put("chainage_from_m", p.getChainageFromM());
      n.put("chainage_to_m", p.getChainageToM());
      n.put("target_qty", p.getTargetQty() == null ? null : p.getTargetQty().doubleValue());
      n.put("unit", p.getUnit());
      n.put("concerns", p.getConcerns());
      n.put("action_by", p.getActionBy());
      n.put("due_date", p.getDueDate() == null ? null : p.getDueDate().toString());
      arr.add(n);
      if (p.getTargetQty() != null) totalTarget = totalTarget.add(p.getTargetQty());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", arr);
    wrapper.put("total_target_qty", totalTarget.doubleValue());
    ToolResult.attachLinks(wrapper, Map.of());
    return wrapper;
  }

  private static LocalDate parseDate(String raw, LocalDate fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return LocalDate.parse(raw.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
