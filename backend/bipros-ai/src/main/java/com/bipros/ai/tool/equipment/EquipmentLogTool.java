package com.bipros.ai.tool.equipment;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.ResourceEquipmentDetailsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Equipment daily logs — operating / idle / breakdown hours, fuel, operator.
 * Action-typed via {@code op}: {@code list}, {@code by_resource},
 * {@code utilization}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentLogTool implements Tool {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;
  // Hours per day available for utilization base when no explicit calendar is wired.
  private static final double HOURS_PER_DAY = 8.0;

  private final EquipmentLogRepository equipmentLogRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceEquipmentDetailsRepository equipmentDetailsRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "equipment_log";
  }

  @Override
  public String description() {
    return "Use this when the user asks about equipment daily logs — \"how many hours did the "
        + "JCB run last week\", \"breakdown hours on excavators\", \"fuel consumed by crane "
        + "X\", \"equipment utilization for April\". One tool, multiple ops via the `op` param: "
        + "`list` (project rows in a date range), `by_resource` (one piece of equipment, date "
        + "range), `utilization` (per-resource aggregate of operating / idle / breakdown hours, "
        + "fuel, with utilization% computed against an 8h/day base over the window). "
        + "Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();

    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("list");
    opEnum.add("by_resource");
    opEnum.add("utilization");
    ObjectNode op = objectMapper.createObjectNode();
    op.put("type", "string");
    op.set("enum", opEnum);
    op.put("description", "Which sub-query to run. Required.");
    props.set("op", op);

    props.set("date_from", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Default: 30 days before date_to."));
    props.set("date_to", objectMapper.createObjectNode().put("type", "string").put("format", "date")
        .put("description", "ISO date. Default: today."));
    props.set("resource_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Equipment resource UUID. Required by `by_resource`."));
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
      return ToolResult.error("equipment_log needs a project in scope. Pick a project, then re-ask.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = orNull(input.path("op").asText(null));
    if (op == null) {
      return ToolResult.error("equipment_log requires `op` ∈ {list, by_resource, utilization}.");
    }
    int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

    LocalDate dateTo = parseDate(input.path("date_to").asText(null), LocalDate.now());
    LocalDate dateFrom = parseDate(input.path("date_from").asText(null), dateTo.minusDays(30));
    if (dateFrom.isAfter(dateTo)) { LocalDate t = dateFrom; dateFrom = dateTo; dateTo = t; }

    return switch (op.toLowerCase()) {
      case "list" -> doList(projectId, dateFrom, dateTo, limit);
      case "by_resource" -> doByResource(input, projectId, dateFrom, dateTo, limit);
      case "utilization" -> doUtilization(projectId, dateFrom, dateTo, limit);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doList(UUID projectId, LocalDate dateFrom, LocalDate dateTo, int limit) {
    List<EquipmentLog> base = equipmentLogRepository.findByProjectIdAndLogDateBetween(
        projectId, dateFrom, dateTo);
    int matched = base.size();
    if (base.size() > limit) base = base.subList(0, limit);

    Map<UUID, Resource> resourceById = loadResources(base);
    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> resourceLinks = new HashSet<>();
    for (EquipmentLog l : base) {
      rows.add(toRow(l, resourceById));
      if (l.getResourceId() != null) resourceLinks.add(l.getResourceId());
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    ToolResult.attachLinks(wrapper, Map.of("resource", new ArrayList<>(resourceLinks)));
    return ToolResult.ok(String.format("%d equipment log row%s between %s and %s.",
        matched, matched == 1 ? "" : "s", dateFrom, dateTo), wrapper);
  }

  private ToolResult doByResource(JsonNode input, UUID projectId,
                                  LocalDate dateFrom, LocalDate dateTo, int limit) {
    UUID resourceId = parseUuid(input.path("resource_id").asText(null));
    if (resourceId == null) {
      return ToolResult.error("by_resource requires `resource_id` (UUID).");
    }
    List<EquipmentLog> base = equipmentLogRepository
        .findByResourceIdAndLogDateBetween(resourceId, dateFrom, dateTo,
            PageRequest.of(0, Math.min(limit, MAX_LIMIT), Sort.by("logDate").ascending()))
        .getContent();
    // Defensive scope: ensure rows belong to the in-scope project.
    base.removeIf(l -> !projectId.equals(l.getProjectId()));

    Map<UUID, Resource> resourceById = loadResources(base);
    ArrayNode rows = objectMapper.createArrayNode();
    double opHours = 0, idleHours = 0, bdHours = 0, fuel = 0;
    for (EquipmentLog l : base) {
      rows.add(toRow(l, resourceById));
      if (l.getOperatingHours() != null) opHours += l.getOperatingHours();
      if (l.getIdleHours() != null) idleHours += l.getIdleHours();
      if (l.getBreakdownHours() != null) bdHours += l.getBreakdownHours();
      if (l.getFuelConsumed() != null) fuel += l.getFuelConsumed();
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("resource_id", resourceId.toString());
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    wrapper.put("returned", rows.size());
    wrapper.put("total_operating_hours", opHours);
    wrapper.put("total_idle_hours", idleHours);
    wrapper.put("total_breakdown_hours", bdHours);
    wrapper.put("total_fuel_consumed", fuel);
    ToolResult.attachLinks(wrapper, Map.of("resource", List.of(resourceId)));
    return ToolResult.ok(String.format(
        "%d log row%s for the equipment between %s and %s — operating %.1fh, idle %.1fh, breakdown %.1fh.",
        rows.size(), rows.size() == 1 ? "" : "s", dateFrom, dateTo, opHours, idleHours, bdHours), wrapper);
  }

  private ToolResult doUtilization(UUID projectId, LocalDate dateFrom, LocalDate dateTo, int limit) {
    List<EquipmentLog> base = equipmentLogRepository.findByProjectIdAndLogDateBetween(
        projectId, dateFrom, dateTo);
    Map<UUID, Resource> resourceById = loadResources(base);

    Map<UUID, Agg> aggByResource = new LinkedHashMap<>();
    for (EquipmentLog l : base) {
      UUID rid = l.getResourceId();
      if (rid == null) continue;
      Agg a = aggByResource.computeIfAbsent(rid, k -> new Agg());
      a.distinctDates.add(l.getLogDate());
      if (l.getOperatingHours() != null) a.operating += l.getOperatingHours();
      if (l.getIdleHours() != null) a.idle += l.getIdleHours();
      if (l.getBreakdownHours() != null) a.breakdown += l.getBreakdownHours();
      if (l.getFuelConsumed() != null) a.fuel += l.getFuelConsumed();
      a.rowCount++;
    }

    // Pull equipment details for capacity / make for richer context (best-effort).
    Map<UUID, ResourceEquipmentDetails> detailsById = new HashMap<>();
    if (!aggByResource.isEmpty()) {
      equipmentDetailsRepository.findAllById(aggByResource.keySet())
          .forEach(d -> detailsById.put(d.getResourceId(), d));
    }

    List<Map.Entry<UUID, Agg>> sorted = new ArrayList<>(aggByResource.entrySet());
    sorted.sort(Comparator.comparingDouble((Map.Entry<UUID, Agg> e) -> e.getValue().operating).reversed());
    if (sorted.size() > limit) sorted = sorted.subList(0, limit);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> resourceLinks = new HashSet<>();
    for (Map.Entry<UUID, Agg> e : sorted) {
      UUID rid = e.getKey();
      Agg a = e.getValue();
      Resource r = resourceById.get(rid);
      ResourceEquipmentDetails d = detailsById.get(rid);
      double availableHours = a.distinctDates.size() * HOURS_PER_DAY;
      Double utilizationPct = availableHours > 0 ? (a.operating / availableHours) * 100.0 : null;
      ObjectNode n = objectMapper.createObjectNode();
      n.put("resource_id", rid.toString());
      n.put("resource_code", r == null ? null : r.getCode());
      n.put("resource_name", r == null ? null : r.getName());
      n.put("days_logged", a.distinctDates.size());
      n.put("rows", a.rowCount);
      n.put("operating_hours", a.operating);
      n.put("idle_hours", a.idle);
      n.put("breakdown_hours", a.breakdown);
      n.put("fuel_consumed", a.fuel);
      n.put("available_hours_base", availableHours);
      n.put("utilization_pct", utilizationPct);
      if (d != null) {
        n.put("make", d.getMake());
        n.put("model", d.getModel());
        n.put("capacity_spec", d.getCapacitySpec());
        n.put("ownership_type", d.getOwnershipType() == null ? null : d.getOwnershipType().name());
      }
      rows.add(n);
      resourceLinks.add(rid);
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("date_from", dateFrom.toString());
    wrapper.put("date_to", dateTo.toString());
    wrapper.put("equipment_count", aggByResource.size());
    wrapper.put("returned", rows.size());
    wrapper.put("hours_per_day_base", HOURS_PER_DAY);
    ToolResult.attachLinks(wrapper, Map.of("resource", new ArrayList<>(resourceLinks)));
    return ToolResult.ok(String.format(
        "%d piece%s of equipment ranked by operating hours (window %s..%s, base %.0fh/day).",
        aggByResource.size(), aggByResource.size() == 1 ? "" : "s", dateFrom, dateTo, HOURS_PER_DAY),
        wrapper);
  }

  // --- helpers -------------------------------------------------------------

  private Map<UUID, Resource> loadResources(List<EquipmentLog> logs) {
    Set<UUID> ids = new HashSet<>();
    for (EquipmentLog l : logs) if (l.getResourceId() != null) ids.add(l.getResourceId());
    Map<UUID, Resource> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    resourceRepository.findAllById(ids).forEach(r -> out.put(r.getId(), r));
    return out;
  }

  private ObjectNode toRow(EquipmentLog l, Map<UUID, Resource> resourceById) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("log_id", l.getId() == null ? null : l.getId().toString());
    n.put("log_date", l.getLogDate() == null ? null : l.getLogDate().toString());
    n.put("resource_id", l.getResourceId() == null ? null : l.getResourceId().toString());
    Resource r = l.getResourceId() == null ? null : resourceById.get(l.getResourceId());
    n.put("resource_code", r == null ? null : r.getCode());
    n.put("resource_name", r == null ? null : r.getName());
    n.put("deployment_site", l.getDeploymentSite());
    n.put("operating_hours", l.getOperatingHours());
    n.put("idle_hours", l.getIdleHours());
    n.put("breakdown_hours", l.getBreakdownHours());
    n.put("fuel_consumed", l.getFuelConsumed());
    n.put("operator_name", l.getOperatorName());
    n.put("status", l.getStatus() == null ? null : l.getStatus().name());
    n.put("remarks", l.getRemarks());
    return n;
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
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

  private static class Agg {
    int rowCount = 0;
    double operating = 0;
    double idle = 0;
    double breakdown = 0;
    double fuel = 0;
    Set<LocalDate> distinctDates = new HashSet<>();
  }
}
