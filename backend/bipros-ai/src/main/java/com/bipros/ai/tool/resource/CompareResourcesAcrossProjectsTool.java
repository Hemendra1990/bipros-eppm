package com.bipros.ai.tool.resource;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.resolver.EffectiveRate;
import com.bipros.ai.resolver.EffectiveRateResolver;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-project resource rate + deployment aggregator. Answers questions like
 * "Mason rate across all my projects", "which projects have an override for the
 * crane operator", "total Mason deployment across the portfolio".
 *
 * <p>Pure JPA. Walks every project in the user's accessible scope (admin → all
 * non-archived projects), token-matches resources by keyword (same logic as
 * find_resource_deployment — across code/name AND role code/name), and resolves
 * the effective rate per (resource × project) via {@link EffectiveRateResolver}
 * so pool overrides are honoured.
 *
 * <p>Returns one row per (resource × project): effective_rate, unit, unit_basis,
 * override_applied, planned/actual unit + cost totals, assignment count. Top-level
 * formula_overrides flags include {@code cross_project_rollup} and (when any row
 * is overridden) {@code rate_overridden_per_project}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareResourcesAcrossProjectsTool implements Tool {

  private final ProjectRepository projectRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository assignmentRepository;
  private final EffectiveRateResolver rateResolver;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "compare_resources_across_projects";
  }

  @Override
  public String description() {
    return "Cross-project resource rate + deployment lookup. Use this for questions that "
        + "span multiple projects: 'Mason rate across all my projects', 'which projects have "
        + "an override on the crane operator', 'total deployment of helpers across the "
        + "portfolio'. Takes a `keyword` (resource role / trade / equipment class — same "
        + "matching as find_resource_deployment) and optional `resource_type` filter "
        + "(LABOR / EQUIPMENT / MATERIAL). Returns one row per (resource × project) with "
        + "effective_rate (pool override → resource base), unit, unit_basis, "
        + "override_applied, planned/actual units + cost totals, and assignment count. "
        + "JPA-backed, override-aware — preferred over query_clickhouse for cross-project "
        + "rate questions because the warehouse cannot see ProjectResource.rateOverride.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "keyword",
        objectMapper
            .createObjectNode()
            .put("type", "string")
            .put(
                "description",
                "Resource role / trade / equipment-class keyword. Token-based, "
                    + "case-insensitive substring match across resource code/name and "
                    + "role code/name. Whitespace, hyphens and simple plurals are "
                    + "tolerated. Examples: 'mason', 'electrician', 'helper', 'crane', "
                    + "'earth moving'."));
    ArrayNode typeEnum = objectMapper.createArrayNode();
    typeEnum.add("LABOR");
    typeEnum.add("EQUIPMENT");
    typeEnum.add("MATERIAL");
    ObjectNode typeNode = objectMapper.createObjectNode();
    typeNode.put("type", "string");
    typeNode.set("enum", typeEnum);
    typeNode.put(
        "description",
        "Optional. Restrict to one resource type. When omitted the keyword decides.");
    props.set("resource_type", typeNode);
    schema.set("properties", props);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("keyword");
    schema.set("required", required);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    String keyword = input.path("keyword").asText("").trim();
    if (keyword.isEmpty()) {
      return ToolResult.error(
          "Provide a `keyword` (e.g. 'mason', 'crane', 'helper').");
    }
    List<String> tokens = tokenize(keyword);
    if (tokens.isEmpty()) {
      return ToolResult.error(
          "Provide a `keyword` with at least one alphanumeric character.");
    }
    String resourceTypeFilter =
        orNull(input.path("resource_type").asText(null));
    if (resourceTypeFilter != null) resourceTypeFilter = resourceTypeFilter.trim().toUpperCase();

    // Resolve the user's accessible project set. Admin → every non-archived.
    List<Project> projects;
    if ("ADMIN".equals(ctx.role())) {
      projects = projectRepository.findAllByArchivedAtIsNull();
    } else {
      List<UUID> scoped = ctx.scopedProjectIds();
      if (scoped == null || scoped.isEmpty()) {
        return ToolResult.error(
            "No accessible projects in your scope. Cross-project rollups need at least "
                + "one project you can read.");
      }
      projects = projectRepository.findAllById(scoped);
    }
    if (projects.isEmpty()) {
      return ToolResult.ok(
          "No projects accessible to you.",
          objectMapper.createObjectNode().set("rows", objectMapper.createArrayNode()));
    }

    ArrayNode rows = objectMapper.createArrayNode();
    int overrideRows = 0;
    int matchedResources = 0;

    for (Project project : projects) {
      UUID projectId = project.getId();
      List<ResourceAssignment> projectAssignments =
          assignmentRepository.findByProjectId(projectId);
      if (projectAssignments.isEmpty()) continue;

      // Hydrate resources and roles for this project.
      Set<UUID> resourceIds = new HashSet<>();
      for (ResourceAssignment a : projectAssignments) {
        if (a.getResourceId() != null) resourceIds.add(a.getResourceId());
      }
      if (resourceIds.isEmpty()) continue;
      Map<UUID, Resource> resourceById = new HashMap<>();
      resourceRepository.findAllById(resourceIds).forEach(r -> resourceById.put(r.getId(), r));

      // Filter by token match + optional resource_type.
      Map<UUID, ResourceRollup> rollups = new HashMap<>();
      for (ResourceAssignment a : projectAssignments) {
        if (a.getResourceId() == null) continue;
        Resource r = resourceById.get(a.getResourceId());
        if (r == null) continue;
        if (resourceTypeFilter != null) {
          String code = r.getResourceType() == null ? null : r.getResourceType().getCode();
          if (code == null || !code.equalsIgnoreCase(resourceTypeFilter)) continue;
        }
        ResourceRole role = r.getRole();
        if (!matchesAllTokens(tokens, r, role)) continue;
        rollups.computeIfAbsent(r.getId(), k -> new ResourceRollup(r)).add(a);
      }
      if (rollups.isEmpty()) continue;

      for (ResourceRollup rr : rollups.values()) {
        EffectiveRate er = rateResolver.resolve(projectId, rr.resource.getId());
        ObjectNode row = objectMapper.createObjectNode();
        row.put("project_code", project.getCode());
        row.put("project_name", project.getName());
        row.put("resource_code", rr.resource.getCode());
        row.put("resource_name", rr.resource.getName());
        row.put(
            "resource_type",
            rr.resource.getResourceType() == null ? null : rr.resource.getResourceType().getCode());
        row.put(
            "role",
            rr.resource.getRole() == null ? null : rr.resource.getRole().getName());
        row.put("effective_rate", er.rate() == null ? null : er.rate().doubleValue());
        row.put("unit", er.unit());
        row.put("unit_basis", er.basis());
        row.put("rate_source", er.source().name());
        row.put("override_applied", er.overrideApplied());
        row.put(
            "base_cost_per_unit",
            rr.resource.getCostPerUnit() == null
                ? null
                : rr.resource.getCostPerUnit().doubleValue());
        row.put("assignment_count", rr.assignmentCount);
        row.put("planned_units_total", rr.plannedUnits);
        row.put("actual_units_total", rr.actualUnits);
        row.put("planned_cost_total", rr.plannedCost);
        row.put("actual_cost_total", rr.actualCost);
        ArrayNode notes = objectMapper.createArrayNode();
        if (er.overrideApplied()) {
          notes.add("rate_overridden_per_project");
          overrideRows++;
        }
        row.set("formula_overrides", notes);
        rows.add(row);
        matchedResources++;
      }
    }

    // Sort by planned_cost_total desc so highest-impact rows surface first.
    List<ObjectNode> sorted = new ArrayList<>();
    rows.forEach(n -> sorted.add((ObjectNode) n));
    sorted.sort(
        Comparator.comparingDouble(
                (ObjectNode n) -> n.get("planned_cost_total").asDouble())
            .reversed());
    ArrayNode sortedArr = objectMapper.createArrayNode();
    sorted.forEach(sortedArr::add);

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.set("rows", sortedArr);
    wrapper.put("keyword", keyword);
    if (resourceTypeFilter != null) wrapper.put("resource_type", resourceTypeFilter);
    wrapper.put("project_count_scanned", projects.size());
    wrapper.put("matched_row_count", sorted.size());
    wrapper.put("override_row_count", overrideRows);
    ArrayNode topNotes = objectMapper.createArrayNode();
    topNotes.add("cross_project_rollup");
    if (overrideRows > 0) topNotes.add("rate_overridden_per_project");
    wrapper.set("formula_overrides", topNotes);

    String summary =
        String.format(
            "%d (resource × project) row%s matching \"%s\" across %d project%s%s",
            sorted.size(),
            sorted.size() == 1 ? "" : "s",
            keyword,
            projects.size(),
            projects.size() == 1 ? "" : "s",
            overrideRows > 0 ? " (" + overrideRows + " with project-specific rate)" : "");
    return ToolResult.ok(summary, wrapper);
  }

  private static List<String> tokenize(String query) {
    String norm = normalize(query);
    if (norm.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    for (String p : norm.split(" ")) {
      if (p.isBlank()) continue;
      if (p.length() > 3 && p.endsWith("s")) p = p.substring(0, p.length() - 1);
      out.add(p);
    }
    return out;
  }

  private static String normalize(String s) {
    if (s == null) return "";
    return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
  }

  private static boolean matchesAllTokens(List<String> tokens, Resource r, ResourceRole role) {
    StringBuilder hay = new StringBuilder();
    if (r != null) {
      hay.append(normalize(r.getCode())).append(' ');
      hay.append(normalize(r.getName())).append(' ');
    }
    if (role != null) {
      hay.append(normalize(role.getCode())).append(' ');
      hay.append(normalize(role.getName())).append(' ');
    }
    if (hay.length() == 0) return false;
    String haystack = hay.toString();
    for (String t : tokens) {
      if (!haystack.contains(t)) return false;
    }
    return true;
  }

  private static String orNull(String s) {
    return (s == null || s.isBlank()) ? null : s.trim();
  }

  private static final class ResourceRollup {
    final Resource resource;
    int assignmentCount;
    double plannedUnits;
    double actualUnits;
    double plannedCost;
    double actualCost;

    ResourceRollup(Resource r) {
      this.resource = r;
    }

    void add(ResourceAssignment a) {
      assignmentCount++;
      if (a.getPlannedUnits() != null) plannedUnits += a.getPlannedUnits();
      if (a.getActualUnits() != null) actualUnits += a.getActualUnits();
      if (a.getPlannedCost() != null) plannedCost += a.getPlannedCost().doubleValue();
      if (a.getActualCost() != null) actualCost += a.getActualCost().doubleValue();
    }
  }
}
