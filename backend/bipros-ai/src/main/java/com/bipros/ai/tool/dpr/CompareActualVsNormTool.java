package com.bipros.ai.tool.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Productivity actual vs norm. Joins {@code DailyActivityResourceOutput} to
 * {@code ProductivityNorm} via the activity's {@code work_activity_id}, the
 * resource's {@code resource_type_id}, and (optionally) a resource-level
 * override norm.
 *
 * <p>Returns variance % per (activity, resource, day) bucket, sorted by
 * worst-performing — the rows the user most likely cares about. Optional
 * {@code min_variance_pct} keeps the noise floor down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareActualVsNormTool implements Tool {

  private final DailyActivityResourceOutputRepository outputRepository;
  private final ActivityRepository activityRepository;
  private final ResourceRepository resourceRepository;
  private final ProductivityNormRepository normRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "compare_actual_vs_norm";
  }

  @Override
  public String description() {
    return "Compare actual productivity against the budgeted productivity norm. Walks the "
        + "daily activity-resource output table in the given date range, looks up the matching "
        + "norm via the activity's work_activity and the resource (or resource-type) override, "
        + "and computes a variance % per bucket. Results are ranked by worst variance. "
        + "Filter by activity, resource type, or a min variance threshold to surface only "
        + "underperforming work. Use this for questions like \"which activities are below "
        + "norm\", \"is the masonry crew slow\", \"productivity vs plan for activity X\". "
        + "Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "date_from",
        objectMapper.createObjectNode().put("type", "string").put("format", "date"));
    props.set(
        "date_to",
        objectMapper.createObjectNode().put("type", "string").put("format", "date"));
    props.set(
        "activity_code",
        objectMapper.createObjectNode().put("type", "string").put("description", "Limit to one activity."));
    props.set(
        "resource_type",
        objectMapper.createObjectNode().put("type", "string").put("description", "Resource type code (e.g. CRANE, MASON)."));
    props.set(
        "min_variance_pct",
        objectMapper
            .createObjectNode()
            .put("type", "number")
            .put("description",
                "Surface only rows where |variance_pct| ≥ this threshold (e.g. 20 for ≥20% off plan). "
                    + "Default 0 (return everything)."));
    props.set(
        "limit",
        objectMapper
            .createObjectNode()
            .put("type", "integer")
            .put("minimum", 1)
            .put("maximum", 500)
            .put("default", 100));
    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) {
      return ToolResult.error("compare_actual_vs_norm needs a project in scope.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    LocalDate dateTo = parseDate(input.path("date_to").asText(null), LocalDate.now());
    LocalDate dateFrom = parseDate(input.path("date_from").asText(null), dateTo.minusDays(30));
    if (dateFrom.isAfter(dateTo)) {
      LocalDate t = dateFrom;
      dateFrom = dateTo;
      dateTo = t;
    }
    int limit = Math.max(1, Math.min(500, input.path("limit").asInt(100)));
    double minVar = input.path("min_variance_pct").asDouble(0);
    String resourceTypeFilter = orNull(input.path("resource_type").asText(null));

    UUID activityFilter = null;
    String activityCode = orNull(input.path("activity_code").asText(null));
    if (activityCode != null) {
      Optional<Activity> a = activityRepository.findByProjectIdAndCode(projectId, activityCode);
      if (a.isEmpty()) {
        return ToolResult.error(
            "Activity " + activityCode + " not found in this project. Try resolve_entity first.");
      }
      activityFilter = a.get().getId();
    }

    List<DailyActivityResourceOutput> base =
        outputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
            projectId, dateFrom, dateTo);

    List<DailyActivityResourceOutput> filtered = new ArrayList<>();
    Set<UUID> activityIds = new HashSet<>();
    Set<UUID> resourceIds = new HashSet<>();
    for (DailyActivityResourceOutput o : base) {
      if (activityFilter != null && !activityFilter.equals(o.getActivityId())) continue;
      filtered.add(o);
      if (o.getActivityId() != null) activityIds.add(o.getActivityId());
      if (o.getResourceId() != null) resourceIds.add(o.getResourceId());
    }

    Map<UUID, Activity> activityById = new HashMap<>();
    activityRepository.findAllById(activityIds).forEach(a -> activityById.put(a.getId(), a));
    Map<UUID, Resource> resourceById = new HashMap<>();
    resourceRepository.findAllById(resourceIds).forEach(r -> resourceById.put(r.getId(), r));

    List<Row> rows = new ArrayList<>();
    int withoutNorm = 0;
    for (DailyActivityResourceOutput o : filtered) {
      Activity a = activityById.get(o.getActivityId());
      Resource r = resourceById.get(o.getResourceId());
      if (a == null || r == null) continue;
      ResourceType rt = r.getResourceType();
      String rtCode = rt == null ? null : rt.getCode();
      if (resourceTypeFilter != null && (rtCode == null || !rtCode.equalsIgnoreCase(resourceTypeFilter))) continue;
      UUID workActivityId = a.getWorkActivityId();
      if (workActivityId == null) {
        withoutNorm++;
        continue;
      }

      Optional<ProductivityNorm> norm =
          normRepository.findFirstByWorkActivityIdAndResourceId(workActivityId, r.getId());
      if (norm.isEmpty() && rt != null) {
        norm = normRepository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeId(
            workActivityId, rt.getId());
      }
      if (norm.isEmpty()) {
        withoutNorm++;
        continue;
      }

      Double normPerDay = norm.get().getOutputPerDay() == null ? null : norm.get().getOutputPerDay().doubleValue();
      Double actualPerDay = null;
      if (o.getQtyExecuted() != null && o.getDaysWorked() != null && o.getDaysWorked() > 0) {
        actualPerDay = o.getQtyExecuted().doubleValue() / o.getDaysWorked();
      }
      Double variancePct = null;
      if (normPerDay != null && normPerDay > 0 && actualPerDay != null) {
        variancePct = ((actualPerDay - normPerDay) / normPerDay) * 100.0;
      }

      if (variancePct != null && Math.abs(variancePct) < minVar) continue;

      rows.add(
          new Row(
              o.getOutputDate(),
              a.getId(),
              a.getCode(),
              a.getName(),
              r.getId(),
              r.getCode(),
              r.getName(),
              rtCode,
              o.getQtyExecuted() == null ? null : o.getQtyExecuted().doubleValue(),
              o.getHoursWorked(),
              o.getDaysWorked(),
              actualPerDay,
              normPerDay,
              variancePct,
              o.getUnit()));
    }

    rows.sort(
        Comparator.comparingDouble(
                (Row x) -> x.variancePct == null ? Double.POSITIVE_INFINITY : x.variancePct)
            .reversed()
            .reversed()); // worst (most negative) first
    if (rows.size() > limit) rows = rows.subList(0, limit);

    ArrayNode rowArr = objectMapper.createArrayNode();
    double sumActual = 0;
    double sumNorm = 0;
    int countWithVar = 0;
    double sumAbsVar = 0;
    for (Row x : rows) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("output_date", x.date == null ? null : x.date.toString());
      n.put("activity_id", x.activityId == null ? null : x.activityId.toString());
      n.put("activity_code", x.activityCode);
      n.put("activity_name", x.activityName);
      n.put("resource_id", x.resourceId == null ? null : x.resourceId.toString());
      n.put("resource_code", x.resourceCode);
      n.put("resource_name", x.resourceName);
      n.put("resource_type", x.resourceType);
      n.put("qty_executed", x.qty);
      n.put("hours_worked", x.hours);
      n.put("days_worked", x.days);
      n.put("actual_per_day", x.actualPerDay);
      n.put("norm_per_day", x.normPerDay);
      n.put("variance_pct", x.variancePct);
      n.put("unit", x.unit);
      rowArr.add(n);
      if (x.actualPerDay != null) sumActual += x.actualPerDay;
      if (x.normPerDay != null) sumNorm += x.normPerDay;
      if (x.variancePct != null) {
        countWithVar++;
        sumAbsVar += Math.abs(x.variancePct);
      }
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rowArr);
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    wrapper.put("matched", rows.size());
    wrapper.put("rows_without_norm", withoutNorm);
    wrapper.put("avg_abs_variance_pct", countWithVar == 0 ? null : sumAbsVar / countWithVar);

    String summary =
        rows.isEmpty()
            ? "No (activity, resource) row had a productivity norm to compare in the window "
                + dateFrom + ".." + dateTo + " (" + withoutNorm + " skipped without norm)."
            : rows.size() + " rows compared (" + dateFrom + ".." + dateTo + "). "
                + "Average |variance| "
                + (countWithVar == 0 ? "-" : String.format("%.1f%%", sumAbsVar / countWithVar))
                + ", " + withoutNorm + " skipped without a norm.";
    return ToolResult.ok(summary, wrapper);
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

  private record Row(
      LocalDate date,
      UUID activityId,
      String activityCode,
      String activityName,
      UUID resourceId,
      String resourceCode,
      String resourceName,
      String resourceType,
      Double qty,
      Double hours,
      Double days,
      Double actualPerDay,
      Double normPerDay,
      Double variancePct,
      String unit) {}
}
