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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lists the resources (labour, equipment, material) assigned to a single
 * activity. The activity can be identified by its short code (the value users
 * see in the UI, e.g. {@code ACT-1.3.5(ii)}) or by its UUID.
 *
 * Joins {@link ResourceAssignment} to {@link Resource} to enrich each row with
 * the resource's code, name, type, unit-of-measure, and unit rate, alongside
 * the assignment's planned vs actual units and cost.
 *
 * Implements {@link Tool} directly (rather than extending {@code ProjectScopedTool})
 * so {@code @Transactional} on {@code execute} actually triggers — the parent
 * class's template-method call to {@code doExecute} is a self-invocation that
 * bypasses Spring's AOP proxy. Without an open Hibernate session, the lazy
 * {@code Resource#resourceType} association throws "no session" when the
 * orchestrator runs the tool on a worker thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListActivityResourcesTool implements Tool {

    private final ResourceAssignmentRepository assignmentRepository;
    private final ResourceRepository resourceRepository;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "list_activity_resources";
    }

    @Override
    public String description() {
        return "List resources assigned to a single activity — labour, equipment, and material. "
                + "Identify the activity by its short code (e.g. ACT-1.3.5(ii)) or by activity_id. "
                + "Optional `resource_types` filter (LABOR, EQUIPMENT, MATERIAL — any combination). "
                + "Returns each resource's code, name, type, planned vs actual units and cost, unit "
                + "of measure, and assignment dates. Use for questions like 'what's deployed on "
                + "ACT-1.3.5(ii)', 'how many cranes on the foundation activity', 'which labour crews "
                + "are working on traffic diversion'. Requires a current project in scope.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();

        props.set("activity_code", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Short code as shown in the UI, e.g. ACT-1.3.5(ii). "
                        + "Either this OR activity_id is required."));
        props.set("activity_id", objectMapper.createObjectNode().put("type", "string")
                .put("format", "uuid")
                .put("description", "Activity UUID. Either this OR activity_code is required."));

        ArrayNode typeEnum = objectMapper.createArrayNode();
        typeEnum.add("LABOR");
        typeEnum.add("EQUIPMENT");
        typeEnum.add("MATERIAL");
        ObjectNode typesNode = objectMapper.createObjectNode();
        typesNode.put("type", "array");
        typesNode.set("items", objectMapper.createObjectNode().put("type", "string").set("enum", typeEnum));
        typesNode.put("description", "Optional. Restrict the result to one or more resource types.");
        props.set("resource_types", typesNode);

        schema.set("properties", props);
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error(
                    "list_activity_resources needs a project in scope. Pick a specific project first, "
                            + "then re-ask. (Resource assignments are project-scoped.)");
        }
        // Mirror ProjectScopedTool's access check.
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }

        UUID activityId = resolveActivityId(input, projectId);
        if (activityId == null) {
            return ToolResult.error(
                    "Could not resolve the activity. Provide either activity_code (e.g. ACT-1.3.5(ii)) "
                            + "or activity_id (UUID). For codes, the activity must belong to the current project.");
        }

        Set<String> typeFilter = parseTypeFilter(input);

        List<ResourceAssignment> assignments = assignmentRepository.findByActivityId(activityId);
        Activity activity = activityRepository.findById(activityId).orElse(null);

        if (assignments.isEmpty()) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("rows", objectMapper.createArrayNode());
            wrapper.put("activity_code", activity != null ? activity.getCode() : null);
            wrapper.put("activity_name", activity != null ? activity.getName() : null);
            return ToolResult.ok("No resources are assigned to this activity yet.", wrapper);
        }

        Set<UUID> resourceIds = assignments.stream()
                .map(ResourceAssignment::getResourceId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<UUID, Resource> resourceById = new HashMap<>();
        if (!resourceIds.isEmpty()) {
            // Inside the @Transactional session, lazily-loaded Resource#resourceType
            // can be safely dereferenced via getResourceType().getCode() below.
            resourceRepository.findAllById(resourceIds).forEach(r -> resourceById.put(r.getId(), r));
        }

        ArrayNode rows = objectMapper.createArrayNode();
        int kept = 0;
        for (ResourceAssignment a : assignments) {
            Resource r = a.getResourceId() == null ? null : resourceById.get(a.getResourceId());
            String type = (r != null && r.getResourceType() != null)
                    ? r.getResourceType().getCode()
                    : null;
            if (typeFilter != null && (type == null || !typeFilter.contains(type.toUpperCase()))) {
                continue;
            }
            kept++;

            ObjectNode row = objectMapper.createObjectNode();
            row.put("code", r != null ? r.getCode() : null);
            row.put("name", r != null ? r.getName() : (a.getResourceId() == null ? "(role-only assignment)" : null));
            row.put("type", type);
            row.put("unit", r != null ? r.getUnit() : null);
            row.put("planned_units", a.getPlannedUnits());
            row.put("actual_units", a.getActualUnits());
            row.put("remaining_units", a.getRemainingUnits());
            row.put("planned_cost", a.getPlannedCost() == null ? null : a.getPlannedCost().doubleValue());
            row.put("actual_cost", a.getActualCost() == null ? null : a.getActualCost().doubleValue());
            row.put("cost_per_unit", r != null && r.getCostPerUnit() != null
                    ? r.getCostPerUnit().doubleValue() : null);
            row.put("planned_start", a.getPlannedStartDate() != null ? a.getPlannedStartDate().toString() : null);
            row.put("planned_finish", a.getPlannedFinishDate() != null ? a.getPlannedFinishDate().toString() : null);
            row.put("actual_start", a.getActualStartDate() != null ? a.getActualStartDate().toString() : null);
            row.put("actual_finish", a.getActualFinishDate() != null ? a.getActualFinishDate().toString() : null);
            rows.add(row);
        }

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("activity_code", activity != null ? activity.getCode() : null);
        wrapper.put("activity_name", activity != null ? activity.getName() : null);

        String summary = String.format(
                "%d resource assignment%s on %s",
                kept,
                kept == 1 ? "" : "s",
                activity != null ? activity.getCode() + " — " + activity.getName() : "this activity"
        );
        if (typeFilter != null && kept < assignments.size()) {
            summary += " (filtered by type)";
        }
        return ToolResult.ok(summary, wrapper);
    }

    private UUID resolveActivityId(JsonNode input, UUID projectId) {
        String idStr = input.path("activity_id").asText(null);
        if (idStr != null && !idStr.isBlank()) {
            try {
                return UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {
                // fall through to code lookup
            }
        }
        String code = input.path("activity_code").asText(null);
        if (code != null && !code.isBlank()) {
            Optional<Activity> a = activityRepository.findByProjectIdAndCode(projectId, code.trim());
            if (a.isPresent()) {
                return a.get().getId();
            }
        }
        return null;
    }

    private Set<String> parseTypeFilter(JsonNode input) {
        JsonNode types = input.path("resource_types");
        if (!types.isArray() || types.isEmpty()) return null;
        Set<String> out = new HashSet<>();
        types.forEach(n -> {
            String v = n.asText(null);
            if (v != null && !v.isBlank()) out.add(v.trim().toUpperCase());
        });
        return out.isEmpty() ? null : out;
    }
}
