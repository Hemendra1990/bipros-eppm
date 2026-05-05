package com.bipros.ai.tool.resource;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.SkillCategory;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LabourReturn-driven manpower analytics. Operations: by_category, attendance, by_supervisor, deployment_summary.
 * <p>
 * Note: LabourReturn doesn't model a supervisor field; the closest signal in this schema is
 * {@code contractorName}, so {@code by_supervisor} aggregates by contractor (the field most
 * site teams treat as "the supervising org"). {@code attendance} is derived from headCount
 * across the date range — {@code ManpowerAttendance} is a per-resource snapshot table without
 * historical reporting, so a true day-by-day attendance trace isn't available here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabourReturnsTool implements Tool {

  private final LabourReturnRepository labourReturnRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "labour_returns";
  }

  @Override
  public String description() {
    return "Use this when the user asks about manpower/labour deployed on site — daily contractor "
        + "labour returns, head count, man-days, breakdown by skill category. Operations via op "
        + "param: 'by_category' (head count + man-days grouped by SKILLED / SEMI_SKILLED / UNSKILLED "
        + "/ SUPERVISOR / ENGINEER), 'attendance' (per-day total head count and man-days, derived "
        + "from labour-return headCount sums), 'by_supervisor' (grouped by contractor_name — the "
        + "schema's closest field to a supervising org), 'deployment_summary' (KPIs: total head "
        + "count, total man-days, average per day). Filter by date_from / date_to (default last 30 "
        + "days). Examples: 'how much labour was deployed last week', 'how many skilled workers on "
        + "site this month', 'which contractor brought the most labour'. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("by_category");
    opEnum.add("attendance");
    opEnum.add("by_supervisor");
    opEnum.add("deployment_summary");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    props.set("op", opNode);
    props.set("date_from", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Default: 30 days before date_to."));
    props.set("date_to", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Default: today."));
    props.set("contractor_name", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Optional case-insensitive substring filter on contractor_name."));
    schema.set("properties", props);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("op");
    schema.set("required", required);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) return ToolResult.error("labour_returns needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required (by_category | attendance | by_supervisor | deployment_summary)");

    LocalDate to = parseDate(input.path("date_to").asText(null), LocalDate.now());
    LocalDate from = parseDate(input.path("date_from").asText(null), to.minusDays(30));
    if (from.isAfter(to)) { LocalDate t = from; from = to; to = t; }
    String contractorFilter = orNull(input.path("contractor_name").asText(null));

    List<LabourReturn> rows = labourReturnRepository.findByProjectIdAndReturnDateBetween(projectId, from, to);
    if (contractorFilter != null) {
      String f = contractorFilter.toLowerCase();
      rows = rows.stream().filter(r -> r.getContractorName() != null && r.getContractorName().toLowerCase().contains(f)).toList();
    }

    return switch (op) {
      case "by_category" -> doByCategory(rows, from, to);
      case "attendance" -> doAttendance(rows, from, to);
      case "by_supervisor" -> doByContractor(rows, from, to);
      case "deployment_summary" -> doDeploymentSummary(rows, from, to);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doByCategory(List<LabourReturn> rows, LocalDate from, LocalDate to) {
    Map<String, long[]> agg = new LinkedHashMap<>();
    for (SkillCategory c : SkillCategory.values()) agg.put(c.name(), new long[]{0, 0});
    for (LabourReturn r : rows) {
      String k = r.getSkillCategory() == null ? "UNKNOWN" : r.getSkillCategory().name();
      long[] sc = agg.computeIfAbsent(k, x -> new long[]{0, 0});
      sc[0] += r.getHeadCount() == null ? 0 : r.getHeadCount();
      sc[1] += r.getManDays() == null ? 0 : Math.round(r.getManDays());
    }
    ArrayNode arr = objectMapper.createArrayNode();
    for (var e : agg.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("category", e.getKey());
      n.put("head_count_sum", e.getValue()[0]);
      n.put("man_days_sum", e.getValue()[1]);
      arr.add(n);
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", arr);
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("returns", rows.size());
    return ToolResult.ok(rows.size() + " labour returns by category " + from + "→" + to, w);
  }

  private ToolResult doAttendance(List<LabourReturn> rows, LocalDate from, LocalDate to) {
    Map<LocalDate, long[]> byDate = new LinkedHashMap<>();
    for (LabourReturn r : rows) {
      long[] sc = byDate.computeIfAbsent(r.getReturnDate(), d -> new long[]{0, 0});
      sc[0] += r.getHeadCount() == null ? 0 : r.getHeadCount();
      sc[1] += r.getManDays() == null ? 0 : Math.round(r.getManDays());
    }
    ArrayNode arr = objectMapper.createArrayNode();
    long totalHc = 0;
    long totalMd = 0;
    for (var e : byDate.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("date", e.getKey().toString());
      n.put("head_count", e.getValue()[0]);
      n.put("man_days", e.getValue()[1]);
      arr.add(n);
      totalHc += e.getValue()[0];
      totalMd += e.getValue()[1];
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", arr);
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("days_with_data", byDate.size());
    w.put("total_head_count", totalHc);
    w.put("total_man_days", totalMd);
    return ToolResult.ok(byDate.size() + " days of attendance " + from + "→" + to, w);
  }

  private ToolResult doByContractor(List<LabourReturn> rows, LocalDate from, LocalDate to) {
    Map<String, long[]> agg = new LinkedHashMap<>();
    for (LabourReturn r : rows) {
      String k = r.getContractorName() == null ? "UNKNOWN" : r.getContractorName();
      long[] sc = agg.computeIfAbsent(k, x -> new long[]{0, 0});
      sc[0] += r.getHeadCount() == null ? 0 : r.getHeadCount();
      sc[1] += r.getManDays() == null ? 0 : Math.round(r.getManDays());
    }
    ArrayNode arr = objectMapper.createArrayNode();
    agg.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).forEach(e -> {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("contractor_name", e.getKey());
      n.put("head_count_sum", e.getValue()[0]);
      n.put("man_days_sum", e.getValue()[1]);
      arr.add(n);
    });
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", arr);
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("contractors", agg.size());
    w.put("note", "LabourReturn has no supervisor field; aggregating by contractor_name as the closest signal.");
    return ToolResult.ok(agg.size() + " contractors deploying labour " + from + "→" + to, w);
  }

  private ToolResult doDeploymentSummary(List<LabourReturn> rows, LocalDate from, LocalDate to) {
    long totalHc = 0;
    long totalMd = 0;
    java.util.Set<LocalDate> dates = new java.util.HashSet<>();
    for (LabourReturn r : rows) {
      totalHc += r.getHeadCount() == null ? 0 : r.getHeadCount();
      totalMd += r.getManDays() == null ? 0 : Math.round(r.getManDays());
      if (r.getReturnDate() != null) dates.add(r.getReturnDate());
    }
    int days = dates.size();
    double avgPerDay = days == 0 ? 0 : ((double) totalHc) / days;
    ObjectNode w = objectMapper.createObjectNode();
    w.put("date_from", from.toString());
    w.put("date_to", to.toString());
    w.put("returns", rows.size());
    w.put("days_with_data", days);
    w.put("total_head_count", totalHc);
    w.put("total_man_days", totalMd);
    w.put("avg_head_count_per_day", avgPerDay);
    return ToolResult.ok("deployment summary: " + totalHc + " head-count, " + totalMd + " man-days over " + days + " days", w);
  }

  private static LocalDate parseDate(String s, LocalDate fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return LocalDate.parse(s.trim()); } catch (Exception e) { return fallback; }
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
