package com.bipros.ai.tool.supervisor;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.query.ResourceContextFacade;
import com.bipros.ai.query.ResourceProfile;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.Resource;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Supervisor view: team enumeration + performance aggregates. Merged into one
 * tool because both anchor on the same supervisor identity — splitting them
 * would force the LLM to pick a tool before knowing whether the user wants
 * roster or numbers, and the most common question ("Who reports to John and
 * how are they performing?") needs both.
 *
 * <p>{@code op} controls what gets returned:
 * <ul>
 *   <li>{@code team} — subordinate roster (org-tree + HR-tree union)</li>
 *   <li>{@code performance} — DPRs filed by the supervisor + output rows logged
 *     for the team's resources, aggregated over the date window</li>
 *   <li>{@code both} (default) — both blocks</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorTool implements Tool {

  private final ResourceContextFacade facade;
  private final ResourceRepository resourceRepository;
  private final DailyProgressReportRepository dprRepository;
  private final DailyActivityResourceOutputRepository outputRepository;
  private final ActivityRepository activityRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "supervisor";
  }

  @Override
  public String description() {
    return "Supervisor / foreman / lead view. Returns the team roster (everyone under this "
        + "supervisor via org-tree OR HR-tree) and/or their performance over a date window "
        + "(DPRs filed, qty executed, hours / days worked, activities touched). Identify the "
        + "supervisor by supervisor_resource_id (preferred) OR by supervisor_name (case-"
        + "insensitive substring; ambiguous names → run resolve_entity first). "
        + "op=\"team\" → roster only. op=\"performance\" → numbers only. op=\"both\" (default) "
        + "→ both blocks. Use this for: \"Who reports to Foreman John?\", \"How is Site "
        + "Engineer Sandeep's team performing?\", \"What did Supervisor Patel's crew deliver "
        + "in March?\". Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "supervisor_resource_id",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("format", "uuid")
            .put("description", "UUID of the supervisor's Resource record. Use this when known."));
    props.set(
        "supervisor_name",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put("description", "Free-text name. Falls back to fuzzy resolution. Use resolve_entity for ambiguous names."));
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("team");
    opEnum.add("performance");
    opEnum.add("both");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    opNode.put("default", "both");
    props.set("op", opNode);
    props.set(
        "date_from",
        objectMapper.createObjectNode().put("type", "string").put("format", "date").put("description", "Default: 30 days before date_to."));
    props.set(
        "date_to",
        objectMapper.createObjectNode().put("type", "string").put("format", "date").put("description", "Default: today."));
    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) {
      return ToolResult.error("supervisor needs a project in scope.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = input.path("op").asText("both").toLowerCase();
    LocalDate dateTo = parseDate(input.path("date_to").asText(null), LocalDate.now());
    LocalDate dateFrom = parseDate(input.path("date_from").asText(null), dateTo.minusDays(30));
    if (dateFrom.isAfter(dateTo)) {
      LocalDate t = dateFrom;
      dateFrom = dateTo;
      dateTo = t;
    }

    UUID supervisorId = resolveSupervisorId(input);
    Resource supervisor = supervisorId == null ? null : resourceRepository.findById(supervisorId).orElse(null);
    String supervisorName = orNull(input.path("supervisor_name").asText(null));
    Optional<ResourceProfile> profileOpt =
        supervisor != null
            ? facade.loadProfile(
                supervisor.getId(),
                EnumSet.of(ResourceContextFacade.Include.MANPOWER, ResourceContextFacade.Include.HIERARCHY))
            : Optional.empty();
    ResourceProfile profile = profileOpt.orElse(null);
    String resolvedName =
        profile != null && profile.manpower() != null && profile.manpower().fullName() != null
            ? profile.manpower().fullName()
            : (supervisor != null ? supervisor.getName() : supervisorName);

    if (supervisor == null && supervisorName == null) {
      return ToolResult.error(
          "Provide either supervisor_resource_id or supervisor_name. Use resolve_entity to convert a name into a UUID.");
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("op", op);
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    if (supervisor != null) {
      wrapper.put("supervisor_resource_id", supervisor.getId().toString());
      wrapper.put("supervisor_code", supervisor.getCode());
      wrapper.put("supervisor_name", resolvedName);
      if (profile != null && profile.manpower() != null) {
        wrapper.put("designation", profile.manpower().designation());
        wrapper.put("category", profile.manpower().category());
      }
      if (profile != null && profile.roleName() != null) {
        wrapper.put("role", profile.roleName());
      }
    } else {
      wrapper.put("supervisor_name", supervisorName);
    }

    if (op.equals("team") || op.equals("both")) {
      ArrayNode teamRows = objectMapper.createArrayNode();
      List<ResourceProfile.Subordinate> subs =
          profile != null ? profile.subordinates() : List.of();
      for (ResourceProfile.Subordinate s : subs) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("resource_id", s.resourceId().toString());
        n.put("code", s.code());
        n.put("name", s.name());
        n.put("full_name", s.fullName());
        n.put("designation", s.designation());
        n.put("role", s.roleName());
        n.put("type_category", s.resourceTypeCategory());
        n.put("link_source", s.linkSource());
        teamRows.add(n);
      }
      wrapper.set("team", teamRows);
      wrapper.put("team_size", teamRows.size());
    }

    Set<UUID> teamResourceIds = new HashSet<>();
    if (profile != null) {
      for (ResourceProfile.Subordinate s : profile.subordinates()) teamResourceIds.add(s.resourceId());
    }

    if (op.equals("performance") || op.equals("both")) {
      String filterName = resolvedName != null ? resolvedName : supervisorName;
      List<DailyProgressReport> dprAll =
          dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
              projectId, dateFrom, dateTo);
      int dprCount = 0;
      Set<String> activitiesTouched = new HashSet<>();
      Set<LocalDate> reportDates = new HashSet<>();
      BigDecimal totalQty = BigDecimal.ZERO;
      Map<String, ActivityRollup> activityRoll = new LinkedHashMap<>();
      for (DailyProgressReport d : dprAll) {
        if (filterName == null) continue;
        if (d.getSupervisorName() == null) continue;
        if (!d.getSupervisorName().equalsIgnoreCase(filterName)
            && !d.getSupervisorName().toLowerCase().contains(filterName.toLowerCase())) continue;
        dprCount++;
        if (d.getActivityName() != null) activitiesTouched.add(d.getActivityName());
        if (d.getReportDate() != null) reportDates.add(d.getReportDate());
        if (d.getQtyExecuted() != null) totalQty = totalQty.add(d.getQtyExecuted());
        if (d.getActivityName() != null) {
          activityRoll
              .computeIfAbsent(d.getActivityName(), k -> new ActivityRollup(k))
              .addDpr(d);
        }
      }

      double totalHours = 0;
      double totalDays = 0;
      Map<UUID, MemberRollup> memberRoll = new LinkedHashMap<>();
      if (!teamResourceIds.isEmpty()) {
        List<DailyActivityResourceOutput> outputs =
            outputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
                projectId, dateFrom, dateTo);
        for (DailyActivityResourceOutput o : outputs) {
          if (o.getResourceId() == null || !teamResourceIds.contains(o.getResourceId())) continue;
          if (o.getHoursWorked() != null) totalHours += o.getHoursWorked();
          if (o.getDaysWorked() != null) totalDays += o.getDaysWorked();
          memberRoll
              .computeIfAbsent(o.getResourceId(), k -> new MemberRollup(k))
              .addOutput(o);
        }
      }

      ObjectNode perf = objectMapper.createObjectNode();
      perf.put("dpr_count", dprCount);
      perf.put("activities_touched", activitiesTouched.size());
      perf.put("report_dates", reportDates.size());
      perf.put("total_qty_executed", totalQty.doubleValue());
      perf.put("total_hours_worked_by_team", totalHours);
      perf.put("total_days_worked_by_team", totalDays);

      ArrayNode topActivities = objectMapper.createArrayNode();
      activityRoll.values().stream()
          .sorted(Comparator.comparingDouble(ActivityRollup::qty).reversed())
          .limit(10)
          .forEach(
              ar -> {
                ObjectNode n = objectMapper.createObjectNode();
                n.put("activity_name", ar.activityName);
                n.put("dpr_count", ar.count);
                n.put("qty_executed", ar.qty);
                topActivities.add(n);
              });
      perf.set("top_activities", topActivities);

      ArrayNode topMembers = objectMapper.createArrayNode();
      Map<UUID, Resource> resourceById = new HashMap<>();
      if (!memberRoll.isEmpty()) {
        resourceRepository.findAllById(memberRoll.keySet()).forEach(r -> resourceById.put(r.getId(), r));
      }
      memberRoll.values().stream()
          .sorted(Comparator.comparingDouble(MemberRollup::days).reversed())
          .limit(15)
          .forEach(
              mr -> {
                Resource r = resourceById.get(mr.resourceId);
                ObjectNode n = objectMapper.createObjectNode();
                n.put("resource_id", mr.resourceId.toString());
                n.put("code", r != null ? r.getCode() : null);
                n.put("name", r != null ? r.getName() : null);
                n.put("hours_worked", mr.hours);
                n.put("days_worked", mr.days);
                n.put("qty_executed", mr.qty);
                n.put("activities_touched", mr.activityIds.size());
                topMembers.add(n);
              });
      perf.set("top_members", topMembers);

      wrapper.set("performance", perf);
    }

    Map<String, List<UUID>> links = new HashMap<>();
    if (supervisor != null) links.put("supervisor", List.of(supervisor.getId()));
    if (!teamResourceIds.isEmpty()) links.put("team_resources", new ArrayList<>(teamResourceIds));
    ToolResult.attachLinks(wrapper, links);

    String header =
        supervisor != null
            ? (resolvedName != null ? resolvedName : supervisor.getName()) + " (" + supervisor.getCode() + ")"
            : supervisorName;
    String summary;
    if (op.equals("team")) {
      summary = header + " — " + (profile == null ? 0 : profile.subordinates().size()) + " on team";
    } else if (op.equals("performance")) {
      summary =
          header
              + " — "
              + wrapper.path("performance").path("dpr_count").asInt()
              + " DPRs, "
              + wrapper.path("performance").path("activities_touched").asInt()
              + " activities, qty "
              + wrapper.path("performance").path("total_qty_executed").asDouble();
    } else {
      summary =
          header
              + " — team "
              + (profile == null ? 0 : profile.subordinates().size())
              + ", "
              + wrapper.path("performance").path("dpr_count").asInt()
              + " DPRs, "
              + wrapper.path("performance").path("activities_touched").asInt()
              + " activities";
    }
    return ToolResult.ok(summary, wrapper);
  }

  private UUID resolveSupervisorId(JsonNode input) {
    String idStr = orNull(input.path("supervisor_resource_id").asText(null));
    if (idStr != null) {
      try {
        return UUID.fromString(idStr);
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    String name = orNull(input.path("supervisor_name").asText(null));
    if (name == null) return null;
    return facade.resolveResourceId(name).orElse(null);
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

  private static class ActivityRollup {
    final String activityName;
    int count = 0;
    double qty = 0;

    ActivityRollup(String activityName) {
      this.activityName = activityName;
    }

    void addDpr(DailyProgressReport d) {
      count++;
      if (d.getQtyExecuted() != null) qty += d.getQtyExecuted().doubleValue();
    }

    double qty() {
      return qty;
    }
  }

  private static class MemberRollup {
    final UUID resourceId;
    double hours = 0;
    double days = 0;
    double qty = 0;
    Set<UUID> activityIds = new HashSet<>();

    MemberRollup(UUID resourceId) {
      this.resourceId = resourceId;
    }

    void addOutput(DailyActivityResourceOutput o) {
      if (o.getHoursWorked() != null) hours += o.getHoursWorked();
      if (o.getDaysWorked() != null) days += o.getDaysWorked();
      if (o.getQtyExecuted() != null) qty += o.getQtyExecuted().doubleValue();
      if (o.getActivityId() != null) activityIds.add(o.getActivityId());
    }

    double days() {
      return days;
    }
  }
}
