package com.bipros.ai.tool.activity;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.WbsNode;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Action-typed WBS query. Operations: tree, subtree, with_progress, by_status, by_phase.
 * Trees are flattened with parent_id + level so the LLM can reconstruct hierarchy
 * without a recursive client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryWbsTool implements Tool {

  private final WbsNodeRepository wbsRepository;
  private final ActivityRepository activityRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "query_wbs";
  }

  @Override
  public String description() {
    return "Use this when you need the Work Breakdown Structure for the current project: full tree, "
        + "a subtree under a chosen WBS code/id, per-node progress rolled up from child activities, "
        + "or filtered by status/phase. Operations via op param: 'tree' (full WBS, root + all "
        + "descendants flattened), 'subtree' (descendants under wbs_code or wbs_id), 'with_progress' "
        + "(tree + activity-derived percent_complete), 'by_status' (count + rows for ACTIVE / "
        + "IN_PROGRESS / NOT_STARTED / COMPLETED / DELAYED / AT_RISK), 'by_phase' (PROGRAMME / "
        + "CONSTRUCTION / MOBILISATION / TENDER / PLANNING). Examples: 'show me the WBS', 'what's "
        + "under package PKG-N03-P01', 'what's the rolled-up progress on the WBS', 'how many WBS "
        + "nodes are AT_RISK'. Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("tree");
    opEnum.add("subtree");
    opEnum.add("with_progress");
    opEnum.add("by_status");
    opEnum.add("by_phase");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    opNode.put("description", "Operation to perform.");
    props.set("op", opNode);
    props.set("wbs_code", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Required for op=subtree (or pass wbs_id)."));
    props.set("wbs_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "Required for op=subtree (or pass wbs_code)."));
    props.set("status", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Required for op=by_status. ACTIVE, IN_PROGRESS, NOT_STARTED, COMPLETED, DELAYED, AT_RISK."));
    props.set("phase", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Required for op=by_phase. PROGRAMME, CONSTRUCTION, MOBILISATION, TENDER, PLANNING."));
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
    if (projectId == null) return ToolResult.error("query_wbs needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }

    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required (tree | subtree | with_progress | by_status | by_phase)");

    return switch (op) {
      case "tree" -> doTree(projectId, false);
      case "with_progress" -> doTree(projectId, true);
      case "subtree" -> doSubtree(input, projectId);
      case "by_status" -> doByStatus(input, projectId);
      case "by_phase" -> doByPhase(input, projectId);
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doTree(UUID projectId, boolean withProgress) {
    List<WbsNode> all = wbsRepository.findByProjectIdOrderBySortOrder(projectId);
    Map<UUID, Double> progressByNode = withProgress ? computeProgressByWbs(projectId) : null;
    ArrayNode rows = objectMapper.createArrayNode();
    for (WbsNode w : all) rows.add(toRow(w, withProgress ? progressByNode.getOrDefault(w.getId(), null) : null));
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("count", all.size());
    wrapper.put("with_progress", withProgress);
    return ToolResult.ok(all.size() + " WBS node" + (all.size() == 1 ? "" : "s"), wrapper);
  }

  private ToolResult doSubtree(JsonNode input, UUID projectId) {
    UUID rootId = resolveWbsId(input, projectId);
    if (rootId == null) return ToolResult.error("Provide wbs_code or wbs_id for op=subtree.");
    List<WbsNode> all = wbsRepository.findByProjectIdOrderBySortOrder(projectId);
    Map<UUID, List<WbsNode>> childrenByParent = new HashMap<>();
    WbsNode root = null;
    for (WbsNode w : all) {
      if (w.getId().equals(rootId)) root = w;
      if (w.getParentId() != null) childrenByParent.computeIfAbsent(w.getParentId(), k -> new ArrayList<>()).add(w);
    }
    if (root == null) return ToolResult.error("WBS node not found in this project.");
    List<WbsNode> walk = new ArrayList<>();
    collectDescendants(root, childrenByParent, walk);
    ArrayNode rows = objectMapper.createArrayNode();
    for (WbsNode w : walk) rows.add(toRow(w, null));
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    wrapper.put("root_code", root.getCode());
    wrapper.put("root_name", root.getName());
    wrapper.put("count", walk.size());
    ToolResult.attachLinks(wrapper, Map.of("wbs", List.of(root.getId())));
    return ToolResult.ok(walk.size() + " nodes under " + root.getCode(), wrapper);
  }

  private ToolResult doByStatus(JsonNode input, UUID projectId) {
    String wanted = orNull(input.path("status").asText(null));
    List<WbsNode> all = wbsRepository.findByProjectIdOrderBySortOrder(projectId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    ArrayNode rows = objectMapper.createArrayNode();
    for (WbsNode w : all) {
      String s = w.getWbsStatus() == null ? "UNSET" : w.getWbsStatus().name();
      counts.merge(s, 1, Integer::sum);
      if (wanted == null || wanted.equalsIgnoreCase(s)) rows.add(toRow(w, null));
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    ArrayNode countRows = objectMapper.createArrayNode();
    for (var e : counts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("status", e.getKey());
      n.put("count", e.getValue());
      countRows.add(n);
    }
    wrapper.set("counts", countRows);
    wrapper.put("filtered_status", wanted);
    return ToolResult.ok(rows.size() + " WBS rows" + (wanted == null ? " (all statuses)" : " with status " + wanted), wrapper);
  }

  private ToolResult doByPhase(JsonNode input, UUID projectId) {
    String wanted = orNull(input.path("phase").asText(null));
    List<WbsNode> all = wbsRepository.findByProjectIdOrderBySortOrder(projectId);
    Map<String, Integer> counts = new LinkedHashMap<>();
    ArrayNode rows = objectMapper.createArrayNode();
    for (WbsNode w : all) {
      String p = w.getPhase() == null ? "UNSET" : w.getPhase().name();
      counts.merge(p, 1, Integer::sum);
      if (wanted == null || wanted.equalsIgnoreCase(p)) rows.add(toRow(w, null));
    }
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", rows);
    ArrayNode countRows = objectMapper.createArrayNode();
    for (var e : counts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("phase", e.getKey());
      n.put("count", e.getValue());
      countRows.add(n);
    }
    wrapper.set("counts", countRows);
    wrapper.put("filtered_phase", wanted);
    return ToolResult.ok(rows.size() + " WBS rows" + (wanted == null ? " (all phases)" : " in phase " + wanted), wrapper);
  }

  /** Roll up percent_complete from leaf activities to each WBS node (mean across activities under-or-equal). */
  private Map<UUID, Double> computeProgressByWbs(UUID projectId) {
    List<Activity> acts = activityRepository.findByProjectId(projectId);
    Map<UUID, double[]> sumCount = new HashMap<>();
    for (Activity a : acts) {
      if (a.getWbsNodeId() == null) continue;
      double[] sc = sumCount.computeIfAbsent(a.getWbsNodeId(), k -> new double[2]);
      sc[0] += (a.getPercentComplete() == null ? 0.0 : a.getPercentComplete());
      sc[1] += 1.0;
    }
    Map<UUID, Double> out = new HashMap<>();
    for (var e : sumCount.entrySet()) {
      double[] sc = e.getValue();
      out.put(e.getKey(), sc[1] == 0 ? null : sc[0] / sc[1]);
    }
    return out;
  }

  private void collectDescendants(WbsNode node, Map<UUID, List<WbsNode>> childrenByParent, List<WbsNode> out) {
    out.add(node);
    List<WbsNode> kids = childrenByParent.get(node.getId());
    if (kids == null) return;
    for (WbsNode k : kids) collectDescendants(k, childrenByParent, out);
  }

  private ObjectNode toRow(WbsNode w, Double rolledUpPct) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("wbs_node_id", w.getId().toString());
    n.put("code", w.getCode());
    n.put("name", w.getName());
    n.put("parent_id", w.getParentId() == null ? null : w.getParentId().toString());
    n.put("level", w.getWbsLevel());
    n.put("type", w.getWbsType() == null ? null : w.getWbsType().name());
    n.put("phase", w.getPhase() == null ? null : w.getPhase().name());
    n.put("status", w.getWbsStatus() == null ? null : w.getWbsStatus().name());
    n.put("sort_order", w.getSortOrder());
    n.put("budget_crores", w.getBudgetCrores() == null ? null : w.getBudgetCrores().doubleValue());
    n.put("chainage_from_m", w.getChainageFromM());
    n.put("chainage_to_m", w.getChainageToM());
    n.put("cost_account_id", w.getCostAccountId() == null ? null : w.getCostAccountId().toString());
    n.put("summary_percent_complete", w.getSummaryPercentComplete());
    if (rolledUpPct != null) n.put("activity_rollup_percent", rolledUpPct);
    return n;
  }

  private UUID resolveWbsId(JsonNode input, UUID projectId) {
    String idStr = orNull(input.path("wbs_id").asText(null));
    if (idStr != null) {
      try {
        return UUID.fromString(idStr);
      } catch (IllegalArgumentException ignored) { /* fall through */ }
    }
    String code = orNull(input.path("wbs_code").asText(null));
    if (code != null) {
      Optional<WbsNode> w = wbsRepository.findByProjectIdAndCode(projectId, code);
      if (w.isPresent()) return w.get().getId();
    }
    return null;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
