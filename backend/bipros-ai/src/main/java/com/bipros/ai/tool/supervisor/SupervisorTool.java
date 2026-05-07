package com.bipros.ai.tool.supervisor;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.query.ResourceContextFacade;
import com.bipros.ai.query.ResourceProfile;
import com.bipros.ai.query.SupervisorPerformance;
import com.bipros.ai.query.SupervisorPerformanceCalculator;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Supervisor view: team enumeration + performance rollup. Merged into one tool
 * because both anchor on the same supervisor identity — splitting them would
 * force the LLM to pick a tool before knowing whether the user wants roster or
 * numbers, and the most common question ("Who reports to John and how are they
 * performing?") needs both.
 *
 * <p>{@code op} controls what gets returned:
 * <ul>
 *   <li>{@code team} — subordinate roster (org-tree + HR-tree union)</li>
 *   <li>{@code performance} — supervised activities + cost / EVM (CPI/SPI) /
 *     schedule rollup, plus DPRs and team output for the date window</li>
 *   <li>{@code both} (default) — both blocks</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorTool implements Tool {

  private final ResourceContextFacade facade;
  private final ResourceRepository resourceRepository;
  private final SupervisorPerformanceCalculator calculator;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "supervisor";
  }

  @Override
  public String description() {
    return "Supervisor / foreman / lead view. Returns the team roster (everyone under this "
        + "supervisor via org-tree OR HR-tree) and a full performance rollup for the supervised "
        + "activities: count + status (not-started / in-progress / completed / delayed), planned vs "
        + "actual cost + variance %, EVM (BAC, PV, EV, AC, CPI, SPI, CV, SV), DPR cadence, qty "
        + "executed, and team hours/days worked over a date window. Identify the supervisor by "
        + "supervisor_resource_id (preferred) OR by supervisor_name (case-insensitive substring; "
        + "ambiguous names → run resolve_entity first). op=\"team\" → roster only. "
        + "op=\"performance\" → numbers only. op=\"both\" (default) → both blocks. Use this for: "
        + "\"Who reports to Foreman John?\", \"How is Site Engineer Sandeep's team performing on "
        + "cost?\", \"What's Patel's CPI / SPI this month?\". For comparing two or more "
        + "supervisors side-by-side, use compare_supervisors instead. Project-scoped.";
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
    String supervisorNameInput = orNull(input.path("supervisor_name").asText(null));

    if (supervisor == null && supervisorNameInput == null) {
      return ToolResult.error(
          "Provide either supervisor_resource_id or supervisor_name. Use resolve_entity to convert a name into a UUID.");
    }

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
            : (supervisor != null ? supervisor.getName() : supervisorNameInput);

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
      wrapper.put("supervisor_name", supervisorNameInput);
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

    SupervisorPerformance perf = null;
    if ((op.equals("performance") || op.equals("both")) && supervisor != null) {
      perf = calculator.compute(projectId, supervisor.getId(), dateFrom, dateTo);
      wrapper.set("performance", renderPerformance(perf));
    } else if (op.equals("performance") || op.equals("both")) {
      ObjectNode err = objectMapper.createObjectNode();
      err.put("error", "performance requires a resolved supervisor_resource_id");
      wrapper.set("performance", err);
    }

    Map<String, List<UUID>> links = new HashMap<>();
    if (supervisor != null) links.put("supervisor", List.of(supervisor.getId()));
    if (profile != null && !profile.subordinates().isEmpty()) {
      List<UUID> teamIds = new ArrayList<>();
      for (ResourceProfile.Subordinate s : profile.subordinates()) teamIds.add(s.resourceId());
      links.put("team_resources", teamIds);
    }
    ToolResult.attachLinks(wrapper, links);

    String header =
        supervisor != null
            ? (resolvedName != null ? resolvedName : supervisor.getName()) + " (" + supervisor.getCode() + ")"
            : supervisorNameInput;
    String summary;
    if (op.equals("team")) {
      summary = header + " — " + (profile == null ? 0 : profile.subordinates().size()) + " on team";
    } else if (op.equals("performance")) {
      summary = perf == null ? header + " — no performance (no resource id)" : performanceSummary(header, perf);
    } else {
      int teamSize = profile == null ? 0 : profile.subordinates().size();
      summary =
          perf == null
              ? header + " — team " + teamSize + ", no performance (no resource id)"
              : performanceSummary(header, perf) + ", team " + teamSize;
    }
    return ToolResult.ok(summary, wrapper);
  }

  private ObjectNode renderPerformance(SupervisorPerformance p) {
    ObjectNode out = objectMapper.createObjectNode();

    ObjectNode scope = objectMapper.createObjectNode();
    scope.put("supervised_activity_count", p.activityScope().total());
    scope.put("not_started", p.activityScope().notStarted());
    scope.put("in_progress", p.activityScope().inProgress());
    scope.put("completed", p.activityScope().completed());
    scope.put("delayed", p.activityScope().delayed());
    if (p.activityScope().avgPctComplete() != null) {
      scope.put("avg_pct_complete", p.activityScope().avgPctComplete());
    }
    ArrayNode codes = objectMapper.createArrayNode();
    for (String c : p.activityScope().topCodes()) codes.add(c);
    scope.set("top_activity_codes", codes);
    out.set("activity_scope", scope);

    ObjectNode cost = objectMapper.createObjectNode();
    cost.put("planned", p.costRollup().planned().toPlainString());
    cost.put("actual", p.costRollup().actual().toPlainString());
    cost.put("remaining", p.costRollup().remaining().toPlainString());
    cost.put("at_completion", p.costRollup().atCompletion().toPlainString());
    cost.put("variance", p.costRollup().variance().toPlainString());
    if (p.costRollup().variancePct() != null) cost.put("variance_pct", p.costRollup().variancePct());
    if (!p.costRollup().overriddenFormulaCodes().isEmpty()) {
      ArrayNode overrides = objectMapper.createArrayNode();
      for (String code : p.costRollup().overriddenFormulaCodes()) overrides.add(code);
      cost.set("formula_overrides", overrides);
    }
    out.set("cost", cost);

    ObjectNode evm = objectMapper.createObjectNode();
    evm.put("bac", p.evmRollup().bac().toPlainString());
    evm.put("pv", p.evmRollup().pv().toPlainString());
    evm.put("ev", p.evmRollup().ev().toPlainString());
    evm.put("ac", p.evmRollup().ac().toPlainString());
    if (p.evmRollup().cpi() != null) evm.put("cpi", p.evmRollup().cpi());
    if (p.evmRollup().spi() != null) evm.put("spi", p.evmRollup().spi());
    evm.put("cv", p.evmRollup().cv().toPlainString());
    evm.put("sv", p.evmRollup().sv().toPlainString());
    evm.put("activity_count_with_evm", p.evmRollup().activityCountWithEvm());
    evm.put("ev_source", p.evmRollup().evSource());
    out.set("evm", evm);

    ObjectNode dpr = objectMapper.createObjectNode();
    dpr.put("dpr_count", p.dprRollup().dprCount());
    dpr.put("distinct_report_dates", p.dprRollup().distinctReportDates());
    dpr.put("activities_touched", p.dprRollup().distinctActivitiesTouched());
    dpr.put("total_qty_executed", p.dprRollup().totalQtyExecuted().toPlainString());
    dpr.put("team_hours_worked", p.dprRollup().totalHoursWorkedByTeam().toPlainString());
    dpr.put("team_days_worked", p.dprRollup().totalDaysWorkedByTeam().toPlainString());
    dpr.put("match_source", p.dprRollup().matchSource());
    out.set("dpr", dpr);

    ArrayNode topActs = objectMapper.createArrayNode();
    for (SupervisorPerformance.ActivityTopRollup r : p.topActivities()) {
      ObjectNode n = objectMapper.createObjectNode();
      if (r.activityId() != null) n.put("activity_id", r.activityId().toString());
      n.put("activity_name", r.activityName());
      if (r.qtyExecuted() != null) n.put("qty_executed", r.qtyExecuted().toPlainString());
      if (r.dprCount() != null) n.put("dpr_count", r.dprCount());
      topActs.add(n);
    }
    out.set("top_activities", topActs);

    ArrayNode topMembers = objectMapper.createArrayNode();
    for (SupervisorPerformance.MemberRollup m : p.topMembers()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("resource_id", m.resourceId().toString());
      n.put("code", m.resourceCode());
      n.put("name", m.resourceName());
      if (m.qtyExecuted() != null) n.put("qty_executed", m.qtyExecuted().toPlainString());
      if (m.hoursWorked() != null) n.put("hours_worked", m.hoursWorked().toPlainString());
      if (m.daysWorked() != null) n.put("days_worked", m.daysWorked().toPlainString());
      if (m.activitiesTouched() != null) n.put("activities_touched", m.activitiesTouched());
      topMembers.add(n);
    }
    out.set("top_members", topMembers);

    return out;
  }

  private static String performanceSummary(String header, SupervisorPerformance p) {
    String cpi = p.evmRollup().cpi() == null ? "n/a" : String.format("%.2f", p.evmRollup().cpi());
    String spi = p.evmRollup().spi() == null ? "n/a" : String.format("%.2f", p.evmRollup().spi());
    BigDecimal varPlain = p.costRollup().variance();
    return header
        + " — "
        + p.activityScope().total()
        + " activities ("
        + p.activityScope().delayed()
        + " delayed), CPI "
        + cpi
        + ", SPI "
        + spi
        + ", cost variance "
        + varPlain.toPlainString()
        + ", "
        + p.dprRollup().dprCount()
        + " DPRs";
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
}
