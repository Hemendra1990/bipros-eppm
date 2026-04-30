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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "analyze_schedule";
    }

    @Override
    public String description() {
        return "Analyze schedule health: critical path activities, slipping activities (late start/finish), and near-critical activities (<5 days float).";
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
        LocalDate horizon = LocalDate.now().plusDays(lookahead);

        List<Activity> activities = activityRepository.findByProjectId(ctx.projectId());

        List<Activity> critical = activities.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsCritical()))
                .toList();

        List<Activity> slipping = activities.stream()
                .filter(a -> a.getActualStartDate() != null && a.getPlannedStartDate() != null
                        && a.getActualStartDate().isAfter(a.getPlannedStartDate()))
                .toList();

        List<Activity> nearCritical = activities.stream()
                .filter(a -> a.getTotalFloat() != null && a.getTotalFloat() > 0 && a.getTotalFloat() < 5)
                .toList();

        ArrayNode arr = objectMapper.createArrayNode();
        for (Activity a : critical) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "critical");
            o.put("float", a.getTotalFloat());
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
            arr.add(o);
        }
        for (Activity a : nearCritical) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", a.getId().toString());
            o.put("code", a.getCode());
            o.put("name", a.getName());
            o.put("type", "near_critical");
            o.put("float", a.getTotalFloat());
            arr.add(o);
        }

        return ToolResult.table("Schedule analysis: " + critical.size() + " critical, " + slipping.size() + " slipping, " + nearCritical.size() + " near-critical",
                arr, new String[]{"id", "code", "name", "type", "float", "planned_start", "actual_start"});
    }
}
