package com.bipros.ai.tool;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds where a resource role / designation is deployed across the current
 * project — answers questions like "how many masons are working", "where are
 * electricians assigned", "show me every helper booking".
 *
 * Matches resources by case-insensitive substring on code or name (e.g. the
 * keyword "mason" matches a resource named "Mason" or "Stone Mason"), then
 * aggregates {@link ResourceAssignment} rows for the current project: total
 * planned/actual units and cost, count of activities, and a top-N list of the
 * activities the role appears on.
 *
 * Implements {@link Tool} directly so {@code @Transactional(readOnly = true)}
 * actually triggers (the parent class's template-method invocation would be a
 * self-call that bypasses Spring's AOP proxy and would blow up on the lazy
 * {@code Resource#resourceType} association under the orchestrator's worker
 * thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FindResourceDeploymentTool implements Tool {

    private final ResourceRepository resourceRepository;
    private final ResourceAssignmentRepository assignmentRepository;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "find_resource_deployment";
    }

    @Override
    public String description() {
        return "Find where a resource role / designation / trade is deployed across the current "
                + "project. Search by keyword (e.g. \"mason\", \"electrician\", \"helper\", \"crane\", "
                + "\"steel fixer\") — matches case-insensitively on the resource's code or name. "
                + "Returns matching resources with total planned vs actual units and cost, the "
                + "count of activities they're on, and the top activities ranked by planned units. "
                + "Use for cross-cutting workforce questions like \"how many masons are working\", "
                + "\"where are the welders\", or \"list every helper booking on this project\". "
                + "Requires a current project in scope.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("query", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Keyword that matches a resource role, trade or designation. "
                        + "Substring match on resource code AND name, case-insensitive. "
                        + "Examples: \"mason\", \"electrician\", \"helper\", \"crane\", \"steel\"."));
        props.set("top_activities", objectMapper.createObjectNode()
                .put("type", "integer").put("minimum", 1).put("maximum", 50).put("default", 10)
                .put("description", "How many of the top activities to list per matching resource."));
        ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        schema.set("required", required);
        schema.set("properties", props);
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error(
                    "find_resource_deployment needs a project in scope. Pick a specific project, "
                            + "then re-ask. (Resource assignments are project-scoped.)");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        String query = input.path("query").asText("").trim();
        if (query.isEmpty()) {
            return ToolResult.error(
                    "Provide a `query` keyword (e.g. \"mason\", \"electrician\", \"helper\"). "
                            + "I'll match it case-insensitively on resource codes and names.");
        }
        int topActivities = Math.max(1, Math.min(50, input.path("top_activities").asInt(10)));

        // Pull all assignments on the project, then narrow to those whose resource
        // matches the keyword. Project-scoped scan keeps this bounded; even on
        // very large projects the assignment count is manageable for an in-memory
        // filter.
        List<ResourceAssignment> projectAssignments = assignmentRepository.findByProjectId(projectId);
        Set<UUID> resourceIds = projectAssignments.stream()
                .map(ResourceAssignment::getResourceId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (resourceIds.isEmpty()) {
            return ToolResult.ok("No resources are assigned on this project yet.",
                    objectMapper.createObjectNode().set("rows", objectMapper.createArrayNode()));
        }

        String needle = query.toLowerCase();
        List<Resource> matchingResources = new ArrayList<>();
        Map<UUID, Resource> byId = new HashMap<>();
        resourceRepository.findAllById(resourceIds).forEach(r -> {
            byId.put(r.getId(), r);
            String code = r.getCode() == null ? "" : r.getCode().toLowerCase();
            String name = r.getName() == null ? "" : r.getName().toLowerCase();
            if (code.contains(needle) || name.contains(needle)) {
                matchingResources.add(r);
            }
        });

        if (matchingResources.isEmpty()) {
            return ToolResult.ok(
                    "No resources matching \"" + query + "\" are deployed on this project.",
                    objectMapper.createObjectNode().set("rows", objectMapper.createArrayNode()));
        }

        Set<UUID> matchingIds = new HashSet<>();
        for (Resource r : matchingResources) matchingIds.add(r.getId());

        // Group assignments by resource and collect the activities each is on.
        Map<UUID, List<ResourceAssignment>> byResource = new HashMap<>();
        Set<UUID> activityIds = new HashSet<>();
        for (ResourceAssignment a : projectAssignments) {
            if (a.getResourceId() != null && matchingIds.contains(a.getResourceId())) {
                byResource.computeIfAbsent(a.getResourceId(), k -> new ArrayList<>()).add(a);
                if (a.getActivityId() != null) activityIds.add(a.getActivityId());
            }
        }
        Map<UUID, Activity> activityById = new HashMap<>();
        if (!activityIds.isEmpty()) {
            activityRepository.findAllById(activityIds).forEach(a -> activityById.put(a.getId(), a));
        }

        ArrayNode rows = objectMapper.createArrayNode();
        int totalAssignments = 0;
        for (Resource r : matchingResources) {
            List<ResourceAssignment> assigns = byResource.getOrDefault(r.getId(), List.of());
            if (assigns.isEmpty()) continue;
            totalAssignments += assigns.size();

            double plannedUnits = sumDouble(assigns, ResourceAssignment::getPlannedUnits);
            double actualUnits = sumDouble(assigns, ResourceAssignment::getActualUnits);
            double remainingUnits = sumDouble(assigns, ResourceAssignment::getRemainingUnits);
            double plannedCost = sumBigDecimal(assigns, ResourceAssignment::getPlannedCost);
            double actualCost = sumBigDecimal(assigns, ResourceAssignment::getActualCost);

            // Top activities for this resource, ranked by planned units desc.
            List<ResourceAssignment> sorted = new ArrayList<>(assigns);
            sorted.sort(Comparator.comparingDouble(
                    (ResourceAssignment a) -> a.getPlannedUnits() == null ? 0.0 : a.getPlannedUnits()
            ).reversed());

            ArrayNode topActs = objectMapper.createArrayNode();
            for (int i = 0; i < Math.min(topActivities, sorted.size()); i++) {
                ResourceAssignment a = sorted.get(i);
                Activity act = a.getActivityId() == null ? null : activityById.get(a.getActivityId());
                ObjectNode actNode = objectMapper.createObjectNode();
                actNode.put("activity_code", act != null ? act.getCode() : null);
                actNode.put("activity_name", act != null ? act.getName() : null);
                actNode.put("planned_units", a.getPlannedUnits());
                actNode.put("actual_units", a.getActualUnits());
                actNode.put("planned_cost", a.getPlannedCost() == null ? null : a.getPlannedCost().doubleValue());
                topActs.add(actNode);
            }

            ObjectNode row = objectMapper.createObjectNode();
            row.put("code", r.getCode());
            row.put("name", r.getName());
            row.put("type", r.getResourceType() != null ? r.getResourceType().getCode() : null);
            row.put("unit", r.getUnit());
            row.put("activity_count", assigns.size());
            row.put("planned_units_total", plannedUnits);
            row.put("actual_units_total", actualUnits);
            row.put("remaining_units_total", remainingUnits);
            row.put("planned_cost_total", plannedCost);
            row.put("actual_cost_total", actualCost);
            row.put("cost_per_unit", r.getCostPerUnit() == null ? null : r.getCostPerUnit().doubleValue());
            row.set("top_activities", topActs);
            rows.add(row);
        }

        // Order matching resources by activity count desc.
        // (ArrayNode is already populated in iteration order; rebuild sorted.)
        List<ObjectNode> rowList = new ArrayList<>();
        rows.forEach(n -> rowList.add((ObjectNode) n));
        rowList.sort(Comparator.comparingInt(
                (ObjectNode n) -> n.get("activity_count").asInt()).reversed());
        ArrayNode sortedRows = objectMapper.createArrayNode();
        rowList.forEach(sortedRows::add);

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", sortedRows);
        wrapper.put("query", query);
        wrapper.put("matching_resources", rowList.size());
        wrapper.put("total_assignments", totalAssignments);

        String summary = String.format(
                "Found %d resource%s matching \"%s\" — %d total assignment%s across %d activit%s",
                rowList.size(), rowList.size() == 1 ? "" : "s", query,
                totalAssignments, totalAssignments == 1 ? "" : "s",
                activityIds.size(), activityIds.size() == 1 ? "y" : "ies"
        );
        return ToolResult.ok(summary, wrapper);
    }

    private static double sumDouble(
            List<ResourceAssignment> assigns,
            java.util.function.Function<ResourceAssignment, Double> getter) {
        double total = 0.0;
        for (ResourceAssignment a : assigns) {
            Double v = getter.apply(a);
            if (v != null) total += v;
        }
        return total;
    }

    private static double sumBigDecimal(
            List<ResourceAssignment> assigns,
            java.util.function.Function<ResourceAssignment, BigDecimal> getter) {
        double total = 0.0;
        for (ResourceAssignment a : assigns) {
            BigDecimal v = getter.apply(a);
            if (v != null) total += v.doubleValue();
        }
        return total;
    }
}
