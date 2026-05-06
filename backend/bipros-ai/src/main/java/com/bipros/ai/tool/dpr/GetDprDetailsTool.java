package com.bipros.ai.tool.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Drill into a single DPR. Two lookup paths:
 *   1. {@code dpr_id} (UUID) — direct fetch
 *   2. {@code report_date} + {@code activity_code} — find DPRs for that activity
 *      on that date (a supervisor may file multiple if work spans chainage segments)
 *
 * <p>Returns the DPR row plus parent links (Activity + WBS) so the LLM can
 * follow up with {@code get_activity_full_context} or {@code query_dpr} without
 * a re-lookup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetDprDetailsTool implements Tool {

  private final DailyProgressReportRepository dprRepository;
  private final ActivityRepository activityRepository;
  private final WbsNodeRepository wbsRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "get_dpr_details";
  }

  @Override
  public String description() {
    return "Fetch one or a small number of DPRs as full records, with linked Activity and WBS "
        + "context. Two lookup modes: by dpr_id (UUID, single record), or by "
        + "report_date + activity_code (zero or more, since a supervisor may split a day's "
        + "work across chainage segments). Use this AFTER query_dpr surfaces an interesting "
        + "row, or when the user names a specific date and activity. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "dpr_id",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("format", "uuid")
            .put("description", "DPR row UUID. Either this OR (report_date + activity_code) is required."));
    props.set(
        "report_date",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("format", "date")
            .put("description", "ISO date. Pair with activity_code."));
    props.set(
        "activity_code",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("description", "Activity short code, e.g. ACT-1.3.5(ii). Pair with report_date."));
    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) {
      return ToolResult.error("get_dpr_details needs a project in scope.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    List<DailyProgressReport> matches = lookup(input, projectId);
    if (matches.isEmpty()) {
      return ToolResult.error(
          "No DPR matched. Provide either dpr_id (UUID) or both report_date and activity_code.");
    }

    Map<String, Activity> activityByName = new HashMap<>();
    Map<UUID, WbsNode> wbsById = new HashMap<>();
    for (DailyProgressReport d : matches) {
      if (d.getActivityName() != null && !activityByName.containsKey(d.getActivityName())) {
        activityRepository.findByProjectIdAndCode(projectId, d.getActivityName())
            .ifPresent(a -> activityByName.put(d.getActivityName(), a));
        // also try name match if code lookup failed
        if (!activityByName.containsKey(d.getActivityName())) {
          for (Activity a : activityRepository.findByProjectId(projectId)) {
            if (d.getActivityName().equalsIgnoreCase(a.getName())) {
              activityByName.put(d.getActivityName(), a);
              break;
            }
          }
        }
      }
      if (d.getWbsNodeId() != null && !wbsById.containsKey(d.getWbsNodeId())) {
        wbsRepository.findById(d.getWbsNodeId()).ifPresent(w -> wbsById.put(d.getWbsNodeId(), w));
      }
    }

    ArrayNode rows = objectMapper.createArrayNode();
    java.util.Set<UUID> linkedActivities = new java.util.LinkedHashSet<>();
    java.util.Set<UUID> linkedWbs = new java.util.LinkedHashSet<>();
    for (DailyProgressReport d : matches) {
      ObjectNode row = objectMapper.createObjectNode();
      row.put("dpr_id", d.getId() == null ? null : d.getId().toString());
      row.put("project_id", d.getProjectId() == null ? null : d.getProjectId().toString());
      row.put("report_date", d.getReportDate() == null ? null : d.getReportDate().toString());
      row.put("supervisor_name", d.getSupervisorName());
      row.put("activity_name", d.getActivityName());
      Activity a = activityByName.get(d.getActivityName());
      if (a != null) {
        row.put("activity_code", a.getCode());
        row.put("activity_id", a.getId().toString());
        row.put("activity_status", a.getStatus() == null ? null : a.getStatus().name());
        row.put("activity_percent_complete", a.getPercentComplete());
        linkedActivities.add(a.getId());
      }
      row.put("wbs_node_id", d.getWbsNodeId() == null ? null : d.getWbsNodeId().toString());
      WbsNode w = d.getWbsNodeId() == null ? null : wbsById.get(d.getWbsNodeId());
      if (w != null) {
        row.put("wbs_code", w.getCode());
        row.put("wbs_name", w.getName());
        linkedWbs.add(w.getId());
      }
      row.put("boq_item_no", d.getBoqItemNo());
      row.put("unit", d.getUnit());
      row.put("qty_executed", d.getQtyExecuted() == null ? null : d.getQtyExecuted().doubleValue());
      // cumulative_qty dropped — it's computed on read by the user-facing list endpoint and
      // adding a per-row repo lookup here would N+1 for AI context queries.
      row.put("chainage_from_m", d.getChainageFromM());
      row.put("chainage_to_m", d.getChainageToM());
      row.put("weather_condition", d.getWeatherCondition());
      row.put("remarks", d.getRemarks());
      rows.add(row);
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("count", matches.size());

    Map<String, List<UUID>> links = new HashMap<>();
    if (!linkedActivities.isEmpty()) links.put("activity", List.copyOf(linkedActivities));
    if (!linkedWbs.isEmpty()) links.put("wbs", List.copyOf(linkedWbs));
    ToolResult.attachLinks(wrapper, links);

    String summary;
    if (matches.size() == 1) {
      DailyProgressReport d = matches.get(0);
      summary =
          "DPR on "
              + d.getReportDate()
              + " — "
              + (d.getActivityName() != null ? d.getActivityName() : "(activity unknown)")
              + " by "
              + (d.getSupervisorName() != null ? d.getSupervisorName() : "?")
              + (d.getQtyExecuted() != null ? ", qty " + d.getQtyExecuted() + " " + d.getUnit() : "");
    } else {
      summary =
          matches.size()
              + " DPRs returned (matching the lookup criteria — supervisors sometimes split a day across chainage segments).";
    }
    return ToolResult.ok(summary, wrapper);
  }

  private List<DailyProgressReport> lookup(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("dpr_id").asText(null));
    if (idStr != null) {
      try {
        UUID id = UUID.fromString(idStr);
        Optional<DailyProgressReport> opt = dprRepository.findById(id);
        if (opt.isPresent() && projectId.equals(opt.get().getProjectId())) {
          return List.of(opt.get());
        }
        return List.of();
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    String dateStr = orNull(input.path("report_date").asText(null));
    String activityCode = orNull(input.path("activity_code").asText(null));
    if (dateStr == null || activityCode == null) return List.of();

    LocalDate date;
    try {
      date = LocalDate.parse(dateStr);
    } catch (Exception e) {
      return List.of();
    }

    Optional<Activity> a = activityRepository.findByProjectIdAndCode(projectId, activityCode);
    if (a.isEmpty()) return List.of();

    List<DailyProgressReport> sameName =
        dprRepository.findByProjectIdAndActivityNameIgnoreCaseOrderByReportDateAsc(
            projectId, a.get().getName());
    List<DailyProgressReport> out = new java.util.ArrayList<>();
    for (DailyProgressReport d : sameName) {
      if (date.equals(d.getReportDate())) out.add(d);
    }
    return out;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
