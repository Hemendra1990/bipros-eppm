package com.bipros.ai.tool.permit;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitIsolationPoint;
import com.bipros.permit.domain.model.PermitPack;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.repository.PermitIsolationPointRepository;
import com.bipros.permit.domain.repository.PermitPackRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Permit-to-Work query tool. Action-typed via {@code op} so the LLM picks one
 * of: {@code list}, {@code get_details}, {@code by_supervisor},
 * {@code by_status}, {@code expiring}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermitsTool implements Tool {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 500;

  private final PermitRepository permitRepository;
  private final PermitTypeTemplateRepository permitTypeTemplateRepository;
  private final PermitPackRepository permitPackRepository;
  private final PermitIsolationPointRepository isolationPointRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "permits";
  }

  @Override
  public String description() {
    return "Use this when the user asks about Permit-to-Work (PTW) records — e.g. \"open hot-work "
        + "permits\", \"who's the supervisor on permit XYZ\", \"permits expiring this week\", "
        + "\"how many permits are pending HSE\". One tool, multiple ops via the `op` param: "
        + "`list` (project rows, optional status filter), `get_details` (full permit + isolation "
        + "points by permit_id), `by_supervisor` (substring match on supervisor_name), "
        + "`by_status` (group counts by status), `expiring` (validTo within next N days). "
        + "Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();

    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("list");
    opEnum.add("get_details");
    opEnum.add("by_supervisor");
    opEnum.add("by_status");
    opEnum.add("expiring");
    ObjectNode op = objectMapper.createObjectNode();
    op.put("type", "string");
    op.set("enum", opEnum);
    op.put("description",
        "Which sub-query to run. Required.");
    props.set("op", op);

    ArrayNode statusEnum = objectMapper.createArrayNode();
    for (PermitStatus s : PermitStatus.values()) statusEnum.add(s.name());
    ObjectNode status = objectMapper.createObjectNode();
    status.put("type", "string");
    status.set("enum", statusEnum);
    status.put("description", "Optional. Filter by permit status (used by `list`).");
    props.set("status", status);

    props.set("permit_id", objectMapper.createObjectNode()
        .put("type", "string").put("format", "uuid")
        .put("description", "Required by `get_details`."));
    props.set("supervisor_name", objectMapper.createObjectNode()
        .put("type", "string")
        .put("description", "Substring (case-insensitive). Required by `by_supervisor`."));
    props.set("days", objectMapper.createObjectNode()
        .put("type", "integer").put("minimum", 1).put("default", 7)
        .put("description", "Window for `expiring`: permits where validTo (or endAt fallback) is "
            + "within the next N days. Default 7."));
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
      return ToolResult.error("permits needs a project in scope. Pick a project, then re-ask.");
    }
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = orNull(input.path("op").asText(null));
    if (op == null) {
      return ToolResult.error("permits requires `op` ∈ {list, get_details, by_supervisor, by_status, expiring}.");
    }
    int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

    return switch (op.toLowerCase()) {
      case "list" -> doList(input, projectId, limit);
      case "get_details" -> doGetDetails(input, projectId);
      case "by_supervisor" -> doBySupervisor(input, projectId, limit);
      case "by_status" -> doByStatus(projectId);
      case "expiring" -> doExpiring(input, projectId, limit);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doList(JsonNode input, UUID projectId, int limit) {
    PermitStatus statusFilter = parseStatus(input.path("status").asText(null));
    List<Permit> all = fetchProjectPermits(projectId);
    List<Permit> filtered = new ArrayList<>();
    for (Permit p : all) {
      if (statusFilter != null && p.getStatus() != statusFilter) continue;
      filtered.add(p);
    }
    int matched = filtered.size();
    if (filtered.size() > limit) filtered = filtered.subList(0, limit);

    Map<UUID, PermitTypeTemplate> typeById = loadTemplates(filtered);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> permitIdLinks = new HashSet<>();
    for (Permit p : filtered) {
      rows.add(toRow(p, typeById));
      permitIdLinks.add(p.getId());
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", matched);
    wrapper.put("returned", rows.size());
    if (statusFilter != null) wrapper.put("status_filter", statusFilter.name());
    ToolResult.attachLinks(wrapper, Map.of("permit", new ArrayList<>(permitIdLinks)));

    String summary = String.format(
        "%d permit%s%s for the project (returned %d).",
        matched, matched == 1 ? "" : "s",
        statusFilter != null ? " in status " + statusFilter : "",
        rows.size());
    return ToolResult.ok(summary, wrapper);
  }

  private ToolResult doGetDetails(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("permit_id").asText(null));
    if (idStr == null) return ToolResult.error("get_details requires `permit_id` (UUID).");
    UUID id;
    try {
      id = UUID.fromString(idStr);
    } catch (IllegalArgumentException e) {
      return ToolResult.error("permit_id is not a valid UUID.");
    }
    Optional<Permit> opt = permitRepository.findById(id);
    if (opt.isEmpty() || !projectId.equals(opt.get().getProjectId())) {
      return ToolResult.error("Permit not found in this project.");
    }
    Permit p = opt.get();
    Map<UUID, PermitTypeTemplate> typeById = loadTemplates(List.of(p));

    ObjectNode row = toRow(p, typeById);
    // Enrich with isolation points and pack info.
    List<PermitIsolationPoint> iso = isolationPointRepository.findByPermitId(p.getId());
    ArrayNode isoArr = objectMapper.createArrayNode();
    for (PermitIsolationPoint ip : iso) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("isolation_point_id", ip.getId() == null ? null : ip.getId().toString());
      n.put("isolation_type", ip.getIsolationType() == null ? null : ip.getIsolationType().name());
      n.put("point_label", ip.getPointLabel());
      n.put("lock_number", ip.getLockNumber());
      n.put("applied_at", ip.getAppliedAt() == null ? null : ip.getAppliedAt().toString());
      n.put("removed_at", ip.getRemovedAt() == null ? null : ip.getRemovedAt().toString());
      isoArr.add(n);
    }
    row.set("isolation_points", isoArr);
    row.put("isolation_point_count", iso.size());
    row.put("task_description", p.getTaskDescription());
    row.put("close_remarks", p.getCloseRemarks());
    row.put("revoke_reason", p.getRevokeReason());
    row.put("suspend_reason", p.getSuspendReason());

    // Best-effort pack listing — packs aren't directly linked to permits, so just include the
    // catalogue of active packs (small set) so the LLM can map a permit type to its standard pack.
    List<PermitPack> packs = permitPackRepository.findAllByActiveTrueOrderBySortOrderAsc();
    ArrayNode packArr = objectMapper.createArrayNode();
    for (PermitPack pk : packs) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("code", pk.getCode());
      n.put("name", pk.getName());
      n.put("description", pk.getDescription());
      packArr.add(n);
    }
    row.set("active_pack_catalogue", packArr);

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("permit", row);
    ToolResult.attachLinks(wrapper, Map.of("permit", List.of(p.getId())));
    String summary = String.format(
        "Permit %s — status %s, %d/%d approvals, %d isolation point%s.",
        p.getPermitCode(), p.getStatus(),
        p.getApprovalsCompleted(), p.getTotalApprovalsRequired(),
        iso.size(), iso.size() == 1 ? "" : "s");
    return ToolResult.ok(summary, wrapper);
  }

  private ToolResult doBySupervisor(JsonNode input, UUID projectId, int limit) {
    String needle = orNull(input.path("supervisor_name").asText(null));
    if (needle == null) return ToolResult.error("by_supervisor requires `supervisor_name`.");
    String lc = needle.toLowerCase();
    List<Permit> all = fetchProjectPermits(projectId);
    List<Permit> match = new ArrayList<>();
    for (Permit p : all) {
      if (p.getSupervisorName() != null && p.getSupervisorName().toLowerCase().contains(lc)) {
        match.add(p);
      }
    }
    int total = match.size();
    if (match.size() > limit) match = match.subList(0, limit);
    Map<UUID, PermitTypeTemplate> typeById = loadTemplates(match);

    ArrayNode rows = objectMapper.createArrayNode();
    for (Permit p : match) rows.add(toRow(p, typeById));

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", total);
    wrapper.put("returned", rows.size());
    wrapper.put("supervisor_filter", needle);
    return ToolResult.ok(String.format("%d permit%s under supervisor matching '%s'.",
        total, total == 1 ? "" : "s", needle), wrapper);
  }

  private ToolResult doByStatus(UUID projectId) {
    Map<PermitStatus, Long> counts = new EnumMap<>(PermitStatus.class);
    for (PermitStatus s : PermitStatus.values()) {
      counts.put(s, permitRepository.countByProjectIdAndStatus(projectId, s));
    }
    ArrayNode rows = objectMapper.createArrayNode();
    long total = 0;
    for (Map.Entry<PermitStatus, Long> e : counts.entrySet()) {
      if (e.getValue() == null || e.getValue() == 0) continue;
      ObjectNode n = objectMapper.createObjectNode();
      n.put("status", e.getKey().name());
      n.put("count", e.getValue());
      rows.add(n);
      total += e.getValue();
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("total", total);
    return ToolResult.ok(String.format("%d permit%s across %d non-empty status bucket%s.",
        total, total == 1 ? "" : "s", rows.size(), rows.size() == 1 ? "" : "s"), wrapper);
  }

  private ToolResult doExpiring(JsonNode input, UUID projectId, int limit) {
    int days = Math.max(1, input.path("days").asInt(7));
    Instant now = Instant.now();
    Instant cutoff = now.plus(days, ChronoUnit.DAYS);
    List<Permit> all = fetchProjectPermits(projectId);
    List<Permit> exp = new ArrayList<>();
    for (Permit p : all) {
      Instant t = p.getValidTo() != null ? p.getValidTo() : p.getEndAt();
      if (t == null) continue;
      if (t.isBefore(now)) continue; // already expired
      if (t.isAfter(cutoff)) continue;
      // Only meaningful for active-ish permits
      switch (p.getStatus()) {
        case CLOSED, REVOKED, EXPIRED, REJECTED -> { continue; }
        default -> { /* keep */ }
      }
      exp.add(p);
    }
    exp.sort(Comparator.comparing((Permit p) ->
        p.getValidTo() != null ? p.getValidTo() : p.getEndAt()));
    int total = exp.size();
    if (exp.size() > limit) exp = exp.subList(0, limit);
    Map<UUID, PermitTypeTemplate> typeById = loadTemplates(exp);

    ArrayNode rows = objectMapper.createArrayNode();
    Set<UUID> permitLinks = new HashSet<>();
    for (Permit p : exp) {
      ObjectNode n = toRow(p, typeById);
      Instant t = p.getValidTo() != null ? p.getValidTo() : p.getEndAt();
      if (t != null) {
        long hoursLeft = ChronoUnit.HOURS.between(now, t);
        n.put("expires_in_hours", hoursLeft);
      }
      rows.add(n);
      permitLinks.add(p.getId());
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("matched", total);
    wrapper.put("returned", rows.size());
    wrapper.put("days_window", days);
    ToolResult.attachLinks(wrapper, Map.of("permit", new ArrayList<>(permitLinks)));
    return ToolResult.ok(String.format("%d permit%s expiring in the next %d day%s.",
        total, total == 1 ? "" : "s", days, days == 1 ? "" : "s"), wrapper);
  }

  // --- helpers -------------------------------------------------------------

  /**
   * No project-scoped finder exists on PermitRepository, so we use the unpaged
   * {@code findAll} with an in-memory project filter. Permits per project are
   * bounded enough that this is acceptable for the AI use case.
   */
  private List<Permit> fetchProjectPermits(UUID projectId) {
    List<Permit> out = new ArrayList<>();
    for (Permit p : permitRepository.findAll()) {
      if (projectId.equals(p.getProjectId())) out.add(p);
    }
    out.sort(Comparator.comparing(Permit::getCreatedAt,
        Comparator.nullsLast(Comparator.reverseOrder())));
    return out;
  }

  private Map<UUID, PermitTypeTemplate> loadTemplates(List<Permit> permits) {
    Set<UUID> ids = new HashSet<>();
    for (Permit p : permits) if (p.getPermitTypeTemplateId() != null) ids.add(p.getPermitTypeTemplateId());
    Map<UUID, PermitTypeTemplate> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    permitTypeTemplateRepository.findAllById(ids).forEach(t -> out.put(t.getId(), t));
    return out;
  }

  private ObjectNode toRow(Permit p, Map<UUID, PermitTypeTemplate> typeById) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("permit_id", p.getId() == null ? null : p.getId().toString());
    n.put("permit_code", p.getPermitCode());
    n.put("status", p.getStatus() == null ? null : p.getStatus().name());
    n.put("risk_level", p.getRiskLevel() == null ? null : p.getRiskLevel().name());
    n.put("supervisor_name", p.getSupervisorName());
    n.put("contractor_org_id", p.getContractorOrgId() == null ? null : p.getContractorOrgId().toString());
    n.put("location_zone", p.getLocationZone());
    n.put("chainage_marker", p.getChainageMarker());
    n.put("start_at", p.getStartAt() == null ? null : p.getStartAt().toString());
    n.put("end_at", p.getEndAt() == null ? null : p.getEndAt().toString());
    n.put("valid_from", p.getValidFrom() == null ? null : p.getValidFrom().toString());
    n.put("valid_to", p.getValidTo() == null ? null : p.getValidTo().toString());
    n.put("approvals_completed", p.getApprovalsCompleted());
    n.put("total_approvals_required", p.getTotalApprovalsRequired());
    n.put("current_approval_step", p.getCurrentApprovalStep());
    n.put("shift", p.getShift() == null ? null : p.getShift().name());
    PermitTypeTemplate t = p.getPermitTypeTemplateId() == null ? null : typeById.get(p.getPermitTypeTemplateId());
    n.put("permit_type_code", t == null ? null : t.getCode());
    n.put("permit_type_name", t == null ? null : t.getName());
    return n;
  }

  private static PermitStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return PermitStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
