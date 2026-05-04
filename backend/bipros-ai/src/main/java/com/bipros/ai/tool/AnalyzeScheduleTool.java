package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeScheduleTool extends ProjectScopedTool {

    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_schedule";
    }

    @Override
    public String description() {
        return "Schedule health analysis — counts of critical-path, slipping, near-critical, and "
                + "in-progress activities. With a current project, returns activity-level rows "
                + "(including in-progress). With no current project (general mode), aggregates "
                + "counts per project across the user's accessible scope. "
                + "For an explicit list of in-progress / almost-done / not-started activities, "
                + "prefer list_activities.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.put("lookahead_days", objectMapper.createObjectNode().put("type", "integer").put("default", 14));
        schema.set("properties", props);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        int lookahead = input.path("lookahead_days").asInt(14);

        List<Activity> activities;
        boolean crossProject = ctx.projectId() == null;

        if (crossProject) {
            if ("ADMIN".equals(ctx.role())) {
                // Admin under the AiContextResolver "empty-scope = unrestricted"
                // convention — fetch all so the per-project rollup below covers
                // the whole portfolio.
                activities = activityRepository.findAll();
            } else {
                List<java.util.UUID> scope = ctx.scopedProjectIds();
                if (scope == null || scope.isEmpty()) {
                    return ToolResult.error("No accessible projects in scope. Call list_projects first to see available projects.");
                }
                activities = activityRepository.findByProjectIdIn(scope);
            }
        } else {
            activities = activityRepository.findByProjectId(ctx.projectId());
        }

        java.util.function.Predicate<Activity> isCritical = a -> Boolean.TRUE.equals(a.getIsCritical());
        java.util.function.Predicate<Activity> isSlipping = a -> a.getActualStartDate() != null
                && a.getPlannedStartDate() != null
                && a.getActualStartDate().isAfter(a.getPlannedStartDate());
        java.util.function.Predicate<Activity> isNearCritical = a -> a.getTotalFloat() != null
                && a.getTotalFloat() > 0 && a.getTotalFloat() < 5;
        java.util.function.Predicate<Activity> isInProgress = a -> {
            Double pct = a.getPercentComplete();
            boolean started = a.getActualStartDate() != null || (pct != null && pct > 0.0);
            boolean unfinished = pct == null || pct < 100.0;
            return started && unfinished;
        };

        ArrayNode arr = objectMapper.createArrayNode();

        if (crossProject) {
            java.util.Map<java.util.UUID, long[]> perProject = new java.util.HashMap<>();
            for (Activity a : activities) {
                long[] counts = perProject.computeIfAbsent(a.getProjectId(), k -> new long[4]);
                if (isCritical.test(a)) counts[0]++;
                if (isSlipping.test(a)) counts[1]++;
                if (isNearCritical.test(a)) counts[2]++;
                if (isInProgress.test(a)) counts[3]++;
            }
            perProject.forEach((projectId, counts) -> {
                ObjectNode o = objectMapper.createObjectNode();
                o.put("project_id", projectId.toString());
                o.put("critical", counts[0]);
                o.put("slipping", counts[1]);
                o.put("near_critical", counts[2]);
                o.put("in_progress", counts[3]);
                arr.add(o);
            });
            return ToolResult.table(
                    "Schedule health per project across " + perProject.size() + " accessible project(s)",
                    arr,
                    new String[]{"project_id", "critical", "slipping", "near_critical", "in_progress"}
            );
        }

        List<Activity> critical = activities.stream().filter(isCritical).toList();
        List<Activity> slipping = activities.stream().filter(isSlipping).toList();
        List<Activity> nearCritical = activities.stream().filter(isNearCritical).toList();
        List<Activity> inProgress = activities.stream().filter(isInProgress).toList();

        for (Activity a : critical) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "critical");
            o.put("float", a.getTotalFloat());
            o.put("percent_complete", a.getPercentComplete());
            arr.add(o);
        }
        for (Activity a : slipping) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "slipping");
            o.put("planned_start", a.getPlannedStartDate() != null ? a.getPlannedStartDate().toString() : null);
            o.put("actual_start", a.getActualStartDate() != null ? a.getActualStartDate().toString() : null);
            o.put("percent_complete", a.getPercentComplete());
            arr.add(o);
        }
        for (Activity a : nearCritical) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "near_critical");
            o.put("float", a.getTotalFloat());
            o.put("percent_complete", a.getPercentComplete());
            arr.add(o);
        }
        for (Activity a : inProgress) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "in_progress");
            o.put("percent_complete", a.getPercentComplete());
            o.put("actual_start", a.getActualStartDate() != null ? a.getActualStartDate().toString() : null);
            o.put("planned_finish", a.getPlannedFinishDate() != null ? a.getPlannedFinishDate().toString() : null);
            arr.add(o);
        }

        return ToolResult.table(
                "Schedule analysis: " + critical.size() + " critical, "
                        + slipping.size() + " slipping, "
                        + nearCritical.size() + " near-critical, "
                        + inProgress.size() + " in progress",
                arr,
                new String[]{"id", "code", "name", "type", "float", "percent_complete",
                        "planned_start", "planned_finish", "actual_start"});
    }
}
