package com.bipros.ai.tool.schedule;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.scheduling.application.dto.WhatIfRequest;
import com.bipros.scheduling.application.dto.WhatIfResponse;
import com.bipros.scheduling.application.service.SchedulingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic schedule what-if / change-impact simulator, callable from chat. Given an activity and
 * a duration change (a delay or a crash), it re-runs the project CPM in memory and reports the impact
 * on the completion date and critical path — answering "what if activity X is delayed by 5 days?".
 *
 * <p>Backed by {@link SchedulingService#simulateWhatIf}; read-only (no persistence). Requires a
 * previously computed schedule (activities + logic) for the project.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulateScheduleWhatIfTool extends ProjectScopedTool {

    private final SchedulingService schedulingService;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "simulate_schedule_whatif";
    }

    @Override
    public String description() {
        return "Simulate the schedule impact of a change and report the effect on the project finish "
                + "date and critical path. Two modes (provide at least one): (1) a single-activity change "
                + "— 'activity' (name or code) plus either 'delta_days' (positive = delay, negative = "
                + "crash) or 'new_duration_days' (absolute); or (2) a high-level scenario 'lever' that "
                + "sweeps many activities at once — add a contractor/equipment crew, a weather delay, or a "
                + "procurement delay. The lever object has 'type' (ADD_RESOURCE, WEATHER_DELAY, "
                + "PROCUREMENT_DELAY), optional 'magnitude' (a percent speed-up for ADD_RESOURCE, a day "
                + "count for the delays), and optional 'keyword' to target activities by name. Optional "
                + "'scenario_label'. Read-only in-memory CPM re-run; needs a project with a built schedule. "
                + "Use for 'what if activity X slips 5 days', 'what if we crash Y by 3 days', or 'what if we "
                + "add a crew / hit a 2-week material delay'.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("activity").put("type", "string")
                .put("description", "Activity name or code to change (single-activity mode)");
        props.putObject("delta_days").put("type", "number")
                .put("description", "Days to add (delay, +) or remove (crash, -) from the activity duration");
        props.putObject("new_duration_days").put("type", "number")
                .put("description", "Absolute new duration in days (alternative to delta_days)");

        ObjectNode lever = props.putObject("lever");
        lever.put("type", "object")
                .put("description", "High-level scenario lever that sweeps many activities at once "
                        + "(alternative to a single-activity change)");
        ObjectNode leverProps = lever.putObject("properties");
        ObjectNode leverType = leverProps.putObject("type");
        leverType.put("type", "string")
                .put("description", "ADD_RESOURCE (speed up work), WEATHER_DELAY, or PROCUREMENT_DELAY");
        leverType.putArray("enum").add("ADD_RESOURCE").add("WEATHER_DELAY").add("PROCUREMENT_DELAY");
        leverProps.putObject("magnitude").put("type", "number")
                .put("description", "ADD_RESOURCE: percent duration reduction (default 15). "
                        + "WEATHER_DELAY: days added (default 5). PROCUREMENT_DELAY: days added (default 7)");
        leverProps.putObject("keyword").put("type", "string")
                .put("description", "Optional: only apply to activities whose name contains this keyword");

        props.putObject("scenario_label").put("type", "string");
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("PLANNING_ENGINEER", "PROJECT_MANAGER", "CONSTRUCTION_MANAGER",
                "PORTFOLIO_MANAGER", "ADMIN");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("simulate_schedule_whatif requires a project in scope.");
        }

        // Optional high-level scenario lever.
        JsonNode leverNode = input.path("lever");
        WhatIfRequest.ScenarioLever lever = null;
        if (leverNode.isObject() && leverNode.hasNonNull("type")) {
            String typeStr = leverNode.path("type").asText().trim().toUpperCase(Locale.ROOT);
            WhatIfRequest.LeverType type;
            try {
                type = WhatIfRequest.LeverType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Unknown lever type \"" + leverNode.path("type").asText()
                        + "\". Use ADD_RESOURCE, WEATHER_DELAY, or PROCUREMENT_DELAY.");
            }
            Double magnitude = leverNode.hasNonNull("magnitude") ? leverNode.path("magnitude").asDouble() : null;
            String keyword = leverNode.hasNonNull("keyword") ? leverNode.path("keyword").asText() : null;
            lever = new WhatIfRequest.ScenarioLever(type, magnitude, keyword);
        }

        // Optional single-activity change.
        String query = input.path("activity").asText(null);
        boolean hasActivity = query != null && !query.isBlank();

        if (!hasActivity && lever == null) {
            return ToolResult.error("Provide either an 'activity' (name or code) to change, or a 'lever' "
                    + "(ADD_RESOURCE, WEATHER_DELAY, or PROCUREMENT_DELAY).");
        }

        Activity target = null;
        Double delta = null;
        List<WhatIfRequest.ActivityChange> changes = List.of();
        if (hasActivity) {
            boolean hasDelta = input.hasNonNull("delta_days");
            boolean hasAbs = input.hasNonNull("new_duration_days");
            if (!hasDelta && !hasAbs) {
                return ToolResult.error("Provide either 'delta_days' (delay +/ crash -) or "
                        + "'new_duration_days' with the 'activity'.");
            }
            target = resolveActivity(projectId, query);
            if (target == null) {
                return ToolResult.error("No activity in this project matches \"" + query + "\".");
            }
            delta = hasDelta ? input.path("delta_days").asDouble() : null;
            Double newDur = hasAbs ? input.path("new_duration_days").asDouble() : null;
            changes = List.of(new WhatIfRequest.ActivityChange(target.getId(), newDur, delta));
        }

        String defaultLabel = target != null
                ? (delta != null ? (delta >= 0 ? "Delay " : "Crash ") + Math.abs(delta) + "d on " : "Set duration of ")
                        + (target.getName() != null ? target.getName() : target.getCode())
                : leverLabel(lever);
        String label = input.path("scenario_label").asText(defaultLabel);

        WhatIfRequest request = new WhatIfRequest(label, changes,
                lever != null ? List.of(lever) : null);

        WhatIfResponse r;
        try {
            r = schedulingService.simulateWhatIf(projectId, request);
        } catch (Exception e) {
            log.warn("simulate_schedule_whatif failed for project {}: {}", projectId, e.getMessage());
            return ToolResult.error("Could not simulate: the project has no built schedule to evaluate "
                    + "(build/calculate the schedule first).");
        }

        ObjectNode data = objectMapper.valueToTree(r);
        String subject = target != null
                ? (target.getName() != null ? target.getName() : target.getCode())
                : label;
        String finishMove = r.deltaWorkingDays() == 0 ? "no change to the project finish"
                : String.format(Locale.ROOT, "the project finish moves %s by %.0f working day%s (%s → %s)",
                        r.deltaWorkingDays() > 0 ? "OUT" : "IN", Math.abs(r.deltaWorkingDays()),
                        Math.abs(r.deltaWorkingDays()) == 1 ? "" : "s", r.baselineFinish(), r.scenarioFinish());
        String crit = r.newlyCritical().isEmpty() ? "no new activities become critical"
                : r.newlyCritical().size() + " activit" + (r.newlyCritical().size() == 1 ? "y becomes" : "ies become")
                        + " newly critical";
        String summary = "What-if on \"" + subject + "\": " + finishMove + "; " + crit + ".";

        ArrayNode cols = data.putArray("columns");
        for (String c : new String[]{"scenarioLabel", "baselineFinish", "scenarioFinish", "deltaWorkingDays",
                "baselineCriticalCount", "scenarioCriticalCount"}) cols.add(c);
        return ToolResult.ok(summary, data);
    }

    /** Human-readable default label for a lever-only scenario. */
    private static String leverLabel(WhatIfRequest.ScenarioLever lever) {
        if (lever == null || lever.type() == null) return "Scenario";
        String scope = (lever.appliesToKeyword() != null && !lever.appliesToKeyword().isBlank())
                ? " (" + lever.appliesToKeyword().trim() + ")" : "";
        return switch (lever.type()) {
            case ADD_RESOURCE -> "Add resource / crew"
                    + (lever.magnitude() != null ? " (-" + lever.magnitude() + "%)" : "") + scope;
            case WEATHER_DELAY -> "Weather delay"
                    + (lever.magnitude() != null ? " (+" + lever.magnitude() + "d)" : "") + scope;
            case PROCUREMENT_DELAY -> "Procurement delay"
                    + (lever.magnitude() != null ? " (+" + lever.magnitude() + "d)" : "") + scope;
        };
    }

    /** Match by exact code, exact name, then case-insensitive contains (shortest name wins). */
    private Activity resolveActivity(UUID projectId, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Activity> all = activityRepository.findByProjectId(projectId);
        Activity contains = null;
        for (Activity a : all) {
            String name = a.getName() == null ? "" : a.getName().toLowerCase(Locale.ROOT);
            String code = a.getCode() == null ? "" : a.getCode().toLowerCase(Locale.ROOT);
            if (code.equals(q) || name.equals(q)) return a;
            if (name.contains(q) && (contains == null || name.length() < contains.getName().length())) {
                contains = a;
            }
        }
        return contains;
    }
}
