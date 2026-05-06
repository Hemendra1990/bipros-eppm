package com.bipros.ai.tool.activity;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Activity dependency / network relationships. Operations: predecessors, successors, by_type, lag_summary.
 * Lag is stored in days on {@code ActivityRelationship.lag} (Double); large lags often indicate
 * external constraints worth surfacing to the LLM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRelationshipsTool implements Tool {

  private static final double LARGE_LAG_THRESHOLD_DAYS = 14.0;

  private final ActivityRelationshipRepository relRepository;
  private final ActivityRepository activityRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "query_relationships";
  }

  @Override
  public String description() {
    return "Use this when you need to inspect activity dependencies (predecessor/successor links) — "
        + "i.e. the project network. Operations via op param: 'predecessors' (list activities that "
        + "must finish/start before a given activity, with relationship type FS/FF/SS/SF + lag), "
        + "'successors' (the activities that depend on a given one), 'by_type' (project-wide count "
        + "of relationships per FS/FF/SS/SF), 'lag_summary' (distribution of lag days, flags "
        + "relationships with lag >= 14 days). Identify activity by activity_code or activity_id "
        + "for predecessors/successors. Examples: 'what are the predecessors of ACT-1.3.5(ii)', "
        + "'show successors', 'how many lag-positive links does the project have'. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("predecessors");
    opEnum.add("successors");
    opEnum.add("by_type");
    opEnum.add("lag_summary");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    opNode.put("description", "Operation to perform.");
    props.set("op", opNode);
    props.set("activity_code", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Activity code (e.g. ACT-1.3.5(ii)). Required for op=predecessors/successors."));
    props.set("activity_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Activity UUID. Either this OR activity_code is required for predecessors/successors."));
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
    if (projectId == null) return ToolResult.error("query_relationships needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required (predecessors | successors | by_type | lag_summary)");

    return switch (op) {
      case "predecessors" -> doNeighbors(input, projectId, true);
      case "successors" -> doNeighbors(input, projectId, false);
      case "by_type" -> doByType(projectId);
      case "lag_summary" -> doLagSummary(projectId);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doNeighbors(JsonNode input, UUID projectId, boolean predecessors) {
    Activity target = resolveActivity(input, projectId);
    if (target == null) return ToolResult.error("Provide activity_code or activity_id.");
    List<ActivityRelationship> rels = predecessors
        ? relRepository.findBySuccessorActivityId(target.getId())
        : relRepository.findByPredecessorActivityId(target.getId());

    List<UUID> otherIds = new ArrayList<>();
    for (ActivityRelationship r : rels) {
      otherIds.add(predecessors ? r.getPredecessorActivityId() : r.getSuccessorActivityId());
    }
    Map<UUID, Activity> byId = new HashMap<>();
    if (!otherIds.isEmpty()) activityRepository.findAllById(otherIds).forEach(a -> byId.put(a.getId(), a));

    ArrayNode rows = objectMapper.createArrayNode();
    for (ActivityRelationship r : rels) {
      UUID otherId = predecessors ? r.getPredecessorActivityId() : r.getSuccessorActivityId();
      Activity other = byId.get(otherId);
      if (other != null && !projectId.equals(other.getProjectId())) continue; // safety: ignore cross-project rels
      ObjectNode n = objectMapper.createObjectNode();
      n.put("activity_id", otherId == null ? null : otherId.toString());
      n.put("activity_code", other == null ? null : other.getCode());
      n.put("activity_name", other == null ? null : other.getName());
      n.put("relationship_type", r.getRelationshipType() == null ? null : shortType(r.getRelationshipType()));
      n.put("lag_days", r.getLag());
      n.put("is_external", r.getIsExternal());
      rows.add(n);
    }

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("activity_id", target.getId().toString());
    wrapper.put("activity_code", target.getCode());
    wrapper.put("activity_name", target.getName());
    wrapper.put("direction", predecessors ? "predecessors" : "successors");
    wrapper.put("count", rows.size());
    Map<String, List<UUID>> links = new HashMap<>();
    if (!otherIds.isEmpty()) links.put("activity", otherIds);
    ToolResult.attachLinks(wrapper, links);
    return ToolResult.ok(rows.size() + " " + (predecessors ? "predecessors" : "successors") + " of " + target.getCode(), wrapper);
  }

  private ToolResult doByType(UUID projectId) {
    List<ActivityRelationship> rels = relRepository.findByProjectId(projectId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    counts.put("FS", 0);
    counts.put("FF", 0);
    counts.put("SS", 0);
    counts.put("SF", 0);
    for (ActivityRelationship r : rels) {
      String t = shortType(r.getRelationshipType());
      if (t == null) continue;
      counts.merge(t, 1, Integer::sum);
    }
    ArrayNode rows = objectMapper.createArrayNode();
    for (var e : counts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("type", e.getKey());
      n.put("count", e.getValue());
      rows.add(n);
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("total_relationships", rels.size());
    return ToolResult.ok(rels.size() + " relationships across project", wrapper);
  }

  private ToolResult doLagSummary(UUID projectId) {
    List<ActivityRelationship> rels = relRepository.findByProjectId(projectId);
    int zeroLag = 0;
    int positive = 0;
    int negative = 0;
    int large = 0;
    double maxLag = 0;
    double minLag = 0;
    List<ActivityRelationship> largeRels = new ArrayList<>();
    for (ActivityRelationship r : rels) {
      double lag = r.getLag() == null ? 0.0 : r.getLag();
      if (lag == 0) zeroLag++;
      else if (lag > 0) positive++;
      else negative++;
      if (lag > maxLag) maxLag = lag;
      if (lag < minLag) minLag = lag;
      if (Math.abs(lag) >= LARGE_LAG_THRESHOLD_DAYS) {
        large++;
        largeRels.add(r);
      }
    }
    Map<UUID, Activity> actBy = new HashMap<>();
    if (!largeRels.isEmpty()) {
      List<UUID> needed = new ArrayList<>();
      for (ActivityRelationship r : largeRels) {
        needed.add(r.getPredecessorActivityId());
        needed.add(r.getSuccessorActivityId());
      }
      activityRepository.findAllById(needed).forEach(a -> actBy.put(a.getId(), a));
    }
    ArrayNode largeRows = objectMapper.createArrayNode();
    for (ActivityRelationship r : largeRels) {
      Activity p = actBy.get(r.getPredecessorActivityId());
      Activity s = actBy.get(r.getSuccessorActivityId());
      ObjectNode n = objectMapper.createObjectNode();
      n.put("predecessor_code", p == null ? null : p.getCode());
      n.put("successor_code", s == null ? null : s.getCode());
      n.put("type", shortType(r.getRelationshipType()));
      n.put("lag_days", r.getLag());
      largeRows.add(n);
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("total", rels.size());
    wrapper.put("zero_lag", zeroLag);
    wrapper.put("positive_lag", positive);
    wrapper.put("negative_lag", negative);
    wrapper.put("max_lag_days", maxLag);
    wrapper.put("min_lag_days", minLag);
    wrapper.put("large_lag_threshold_days", LARGE_LAG_THRESHOLD_DAYS);
    wrapper.put("large_lag_count", large);
    wrapper.set("large_lag_rows", largeRows);
    return ToolResult.ok("lag summary: " + rels.size() + " relationships, " + large + " with |lag|>=" + LARGE_LAG_THRESHOLD_DAYS + "d", wrapper);
  }

  private Activity resolveActivity(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("activity_id").asText(null));
    if (idStr != null) {
      try {
        UUID id = UUID.fromString(idStr);
        return activityRepository.findById(id).filter(a -> projectId.equals(a.getProjectId())).orElse(null);
      } catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    String code = orNull(input.path("activity_code").asText(null));
    if (code != null) {
      Optional<Activity> a = activityRepository.findByProjectIdAndCode(projectId, code);
      if (a.isPresent()) return a.get();
    }
    return null;
  }

  private static String shortType(RelationshipType rt) {
    if (rt == null) return null;
    return switch (rt) {
      case FINISH_TO_START -> "FS";
      case FINISH_TO_FINISH -> "FF";
      case START_TO_START -> "SS";
      case START_TO_FINISH -> "SF";
    };
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
