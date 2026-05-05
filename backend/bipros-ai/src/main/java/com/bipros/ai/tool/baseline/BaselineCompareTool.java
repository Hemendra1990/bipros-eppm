package com.bipros.ai.tool.baseline;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Baseline vs current comparison. Action-typed via {@code op}:
 * list_baselines / compare_schedule / compare_cost / activity_diff.
 *
 * <p>Cost side intentionally aggregates {@link ActivityExpense#getBudgetedCost()} as the
 * "current" plan because Activity itself does not carry a money column.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineCompareTool implements Tool {

    private final BaselineRepository baselineRepository;
    private final BaselineActivityRepository baselineActivityRepository;
    private final ActivityRepository activityRepository;
    private final ActivityExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "baseline_compare";
    }

    @Override
    public String description() {
        return "Use this when the user asks how the project is tracking against a saved baseline — "
                + "schedule slip, cost variance vs budget, baseline list. Operations via `op`: "
                + "`list_baselines` (all baselines for the project, with active flag), "
                + "`compare_schedule` (per-activity early-start/finish baseline vs current dates and "
                + "days slipped, sorted by largest slip), `compare_cost` (sum of baseline planned cost "
                + "vs current ActivityExpense.budgetedCost, by activity), `activity_diff` (side-by-side "
                + "for one activity by code or id). Examples: \"how is schedule tracking against "
                + "baseline\", \"top schedule slips\", \"baseline cost variance\", \"compare baseline "
                + "to current for ACT-1.3.5\". Project-scoped.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        ArrayNode opEnum = objectMapper.createArrayNode();
        opEnum.add("list_baselines");
        opEnum.add("compare_schedule");
        opEnum.add("compare_cost");
        opEnum.add("activity_diff");
        ObjectNode op = objectMapper.createObjectNode();
        op.put("type", "string");
        op.set("enum", opEnum);
        op.put("default", "list_baselines");
        op.put("description", "Operation to run.");
        props.set("op", op);
        props.set("baseline_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
                .put("description", "Specific baseline to compare against. Defaults to the active baseline."));
        props.set("activity_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
                .put("description", "Single activity for `activity_diff`."));
        props.set("activity_code", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Single activity short code for `activity_diff` (e.g. ACT-1.3.5)."));
        props.set("limit", objectMapper.createObjectNode().put("type", "integer")
                .put("default", 50).put("minimum", 1).put("maximum", 500)
                .put("description", "Max rows returned for compare_schedule/compare_cost."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("baseline_compare needs a project in scope.");
        }
        if (!"ADMIN".equals(ctx.role())
                && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
            throw new AccessDeniedException("project not in user scope");
        }
        String op = orDefault(input.path("op").asText(null), "list_baselines");
        return switch (op) {
            case "compare_schedule" -> opCompareSchedule(projectId, input);
            case "compare_cost" -> opCompareCost(projectId, input);
            case "activity_diff" -> opActivityDiff(projectId, input);
            default -> opListBaselines(projectId);
        };
    }

    private ToolResult opListBaselines(UUID projectId) {
        List<Baseline> all = baselineRepository.findByProjectId(projectId);
        ArrayNode rows = objectMapper.createArrayNode();
        int activeCount = 0;
        for (Baseline b : all) {
            if (Boolean.TRUE.equals(b.getIsActive())) activeCount++;
            rows.add(toBaselineRow(b));
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("matched", all.size());
        wrapper.put("active_count", activeCount);
        return ToolResult.ok(String.format("%d baseline%s on project (%d active).",
                all.size(), all.size() == 1 ? "" : "s", activeCount), wrapper);
    }

    private ToolResult opCompareSchedule(UUID projectId, JsonNode input) {
        Baseline baseline = pickBaseline(projectId, input);
        if (baseline == null) return ToolResult.error("No active baseline; pass baseline_id explicitly.");
        int limit = Math.max(1, Math.min(500, input.path("limit").asInt(50)));

        List<BaselineActivity> snap = baselineActivityRepository.findByBaselineId(baseline.getId());
        Map<UUID, Activity> currentById = new HashMap<>();
        activityRepository.findByProjectId(projectId).forEach(a -> currentById.put(a.getId(), a));

        List<ScheduleDiff> diffs = new ArrayList<>();
        for (BaselineActivity ba : snap) {
            Activity cur = currentById.get(ba.getActivityId());
            ScheduleDiff d = new ScheduleDiff();
            d.activityId = ba.getActivityId();
            d.code = cur == null ? null : cur.getCode();
            d.name = cur == null ? null : cur.getName();
            d.baselineStart = ba.getEarlyStart();
            d.baselineFinish = ba.getEarlyFinish();
            d.currentStart = cur == null ? null : (cur.getActualStartDate() != null ? cur.getActualStartDate() : cur.getPlannedStartDate());
            d.currentFinish = cur == null ? null : (cur.getActualFinishDate() != null ? cur.getActualFinishDate() : cur.getPlannedFinishDate());
            d.startSlipDays = daysBetween(d.baselineStart, d.currentStart);
            d.finishSlipDays = daysBetween(d.baselineFinish, d.currentFinish);
            d.percentComplete = cur == null ? null : cur.getPercentComplete();
            diffs.add(d);
        }
        diffs.sort(Comparator.comparingLong((ScheduleDiff d) -> Math.abs(d.finishSlipDays == null ? 0 : d.finishSlipDays)).reversed());

        long onTime = 0, slipped = 0, ahead = 0;
        long totalFinishSlip = 0;
        for (ScheduleDiff d : diffs) {
            if (d.finishSlipDays == null) continue;
            if (d.finishSlipDays > 0) slipped++;
            else if (d.finishSlipDays < 0) ahead++;
            else onTime++;
            totalFinishSlip += d.finishSlipDays;
        }

        List<ScheduleDiff> capped = diffs.size() > limit ? diffs.subList(0, limit) : diffs;
        ArrayNode rows = objectMapper.createArrayNode();
        for (ScheduleDiff d : capped) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("activity_id", d.activityId == null ? null : d.activityId.toString());
            n.put("activity_code", d.code);
            n.put("activity_name", d.name);
            n.put("baseline_start", d.baselineStart == null ? null : d.baselineStart.toString());
            n.put("baseline_finish", d.baselineFinish == null ? null : d.baselineFinish.toString());
            n.put("current_start", d.currentStart == null ? null : d.currentStart.toString());
            n.put("current_finish", d.currentFinish == null ? null : d.currentFinish.toString());
            n.put("start_slip_days", d.startSlipDays);
            n.put("finish_slip_days", d.finishSlipDays);
            n.put("percent_complete", d.percentComplete);
            rows.add(n);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.set("baseline", toBaselineRow(baseline));
        wrapper.put("matched", diffs.size());
        wrapper.put("returned", capped.size());
        wrapper.put("slipped_count", slipped);
        wrapper.put("ahead_count", ahead);
        wrapper.put("on_time_count", onTime);
        wrapper.put("total_finish_slip_days", totalFinishSlip);
        ToolResult.attachLinks(wrapper, Map.of("baseline", List.of(baseline.getId())));
        return ToolResult.ok(String.format("Schedule slip vs %s: %d slipped, %d ahead (total %d days).",
                baseline.getName(), slipped, ahead, totalFinishSlip), wrapper);
    }

    private ToolResult opCompareCost(UUID projectId, JsonNode input) {
        Baseline baseline = pickBaseline(projectId, input);
        if (baseline == null) return ToolResult.error("No active baseline; pass baseline_id explicitly.");
        int limit = Math.max(1, Math.min(500, input.path("limit").asInt(50)));

        List<BaselineActivity> snap = baselineActivityRepository.findByBaselineId(baseline.getId());
        // Aggregate baseline plannedCost by activityId
        Map<UUID, BigDecimal> baselinePlanned = new HashMap<>();
        for (BaselineActivity ba : snap) {
            if (ba.getActivityId() == null) continue;
            BigDecimal v = ba.getPlannedCost() == null ? BigDecimal.ZERO : ba.getPlannedCost();
            baselinePlanned.merge(ba.getActivityId(), v, BigDecimal::add);
        }
        // Aggregate current ActivityExpense.budgetedCost + actualCost by activityId
        List<ActivityExpense> exp = expenseRepository.findByProjectId(projectId);
        Map<UUID, BigDecimal> currentBudget = new HashMap<>();
        Map<UUID, BigDecimal> currentActual = new HashMap<>();
        for (ActivityExpense e : exp) {
            if (e.getActivityId() == null) continue;
            currentBudget.merge(e.getActivityId(),
                    e.getBudgetedCost() == null ? BigDecimal.ZERO : e.getBudgetedCost(), BigDecimal::add);
            currentActual.merge(e.getActivityId(),
                    e.getActualCost() == null ? BigDecimal.ZERO : e.getActualCost(), BigDecimal::add);
        }
        Map<UUID, Activity> currentById = new HashMap<>();
        activityRepository.findByProjectId(projectId).forEach(a -> currentById.put(a.getId(), a));

        List<CostDiff> diffs = new ArrayList<>();
        java.util.Set<UUID> allIds = new java.util.HashSet<>();
        allIds.addAll(baselinePlanned.keySet());
        allIds.addAll(currentBudget.keySet());
        for (UUID actId : allIds) {
            CostDiff d = new CostDiff();
            d.activityId = actId;
            Activity a = currentById.get(actId);
            d.code = a == null ? null : a.getCode();
            d.name = a == null ? null : a.getName();
            d.baselinePlanned = baselinePlanned.getOrDefault(actId, BigDecimal.ZERO);
            d.currentBudget = currentBudget.getOrDefault(actId, BigDecimal.ZERO);
            d.currentActual = currentActual.getOrDefault(actId, BigDecimal.ZERO);
            d.variance = d.currentBudget.subtract(d.baselinePlanned);
            diffs.add(d);
        }
        diffs.sort(Comparator.comparing((CostDiff d) -> d.variance.abs()).reversed());

        BigDecimal totalBaseline = BigDecimal.ZERO;
        BigDecimal totalCurrentBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (CostDiff d : diffs) {
            totalBaseline = totalBaseline.add(d.baselinePlanned);
            totalCurrentBudget = totalCurrentBudget.add(d.currentBudget);
            totalActual = totalActual.add(d.currentActual);
        }

        List<CostDiff> capped = diffs.size() > limit ? diffs.subList(0, limit) : diffs;
        ArrayNode rows = objectMapper.createArrayNode();
        for (CostDiff d : capped) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("activity_id", d.activityId == null ? null : d.activityId.toString());
            n.put("activity_code", d.code);
            n.put("activity_name", d.name);
            n.put("baseline_planned_cost", d.baselinePlanned.doubleValue());
            n.put("current_budgeted_cost", d.currentBudget.doubleValue());
            n.put("current_actual_cost", d.currentActual.doubleValue());
            n.put("variance", d.variance.doubleValue());
            rows.add(n);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.set("baseline", toBaselineRow(baseline));
        ObjectNode totals = objectMapper.createObjectNode();
        totals.put("baseline_planned_cost", totalBaseline.doubleValue());
        totals.put("current_budgeted_cost", totalCurrentBudget.doubleValue());
        totals.put("current_actual_cost", totalActual.doubleValue());
        totals.put("variance", totalCurrentBudget.subtract(totalBaseline).doubleValue());
        wrapper.set("totals", totals);
        wrapper.put("matched", diffs.size());
        wrapper.put("returned", capped.size());
        ToolResult.attachLinks(wrapper, Map.of("baseline", List.of(baseline.getId())));
        return ToolResult.ok(String.format("Cost vs %s — baseline %.0f, current budget %.0f, actual %.0f.",
                baseline.getName(), totalBaseline.doubleValue(), totalCurrentBudget.doubleValue(),
                totalActual.doubleValue()), wrapper);
    }

    private ToolResult opActivityDiff(UUID projectId, JsonNode input) {
        Baseline baseline = pickBaseline(projectId, input);
        if (baseline == null) return ToolResult.error("No active baseline; pass baseline_id explicitly.");
        Activity activity = resolveActivity(projectId, input);
        if (activity == null) return ToolResult.error("Provide activity_id or activity_code.");

        Optional<BaselineActivity> baOpt = baselineActivityRepository.findByBaselineIdAndActivityId(
                baseline.getId(), activity.getId());
        BaselineActivity ba = baOpt.orElse(null);
        List<ActivityExpense> exp = expenseRepository.findByProjectIdAndActivityId(projectId, activity.getId());
        BigDecimal currentBudget = BigDecimal.ZERO;
        BigDecimal currentActual = BigDecimal.ZERO;
        for (ActivityExpense e : exp) {
            currentBudget = currentBudget.add(e.getBudgetedCost() == null ? BigDecimal.ZERO : e.getBudgetedCost());
            currentActual = currentActual.add(e.getActualCost() == null ? BigDecimal.ZERO : e.getActualCost());
        }

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("baseline", toBaselineRow(baseline));

        ObjectNode acttNode = objectMapper.createObjectNode();
        acttNode.put("activity_id", activity.getId().toString());
        acttNode.put("activity_code", activity.getCode());
        acttNode.put("activity_name", activity.getName());
        wrapper.set("activity", acttNode);

        ObjectNode schedule = objectMapper.createObjectNode();
        LocalDate baStart = ba == null ? null : ba.getEarlyStart();
        LocalDate baFinish = ba == null ? null : ba.getEarlyFinish();
        LocalDate curStart = activity.getActualStartDate() != null ? activity.getActualStartDate() : activity.getPlannedStartDate();
        LocalDate curFinish = activity.getActualFinishDate() != null ? activity.getActualFinishDate() : activity.getPlannedFinishDate();
        schedule.put("baseline_start", baStart == null ? null : baStart.toString());
        schedule.put("baseline_finish", baFinish == null ? null : baFinish.toString());
        schedule.put("current_start", curStart == null ? null : curStart.toString());
        schedule.put("current_finish", curFinish == null ? null : curFinish.toString());
        schedule.put("start_slip_days", daysBetween(baStart, curStart));
        schedule.put("finish_slip_days", daysBetween(baFinish, curFinish));
        schedule.put("baseline_original_duration", ba == null ? null : ba.getOriginalDuration());
        schedule.put("current_original_duration", activity.getOriginalDuration());
        schedule.put("baseline_total_float", ba == null ? null : ba.getTotalFloat());
        schedule.put("current_total_float", activity.getTotalFloat());
        wrapper.set("schedule", schedule);

        ObjectNode cost = objectMapper.createObjectNode();
        cost.put("baseline_planned_cost",
                ba != null && ba.getPlannedCost() != null ? ba.getPlannedCost().doubleValue() : null);
        cost.put("baseline_actual_cost",
                ba != null && ba.getActualCost() != null ? ba.getActualCost().doubleValue() : null);
        cost.put("current_budgeted_cost", currentBudget.doubleValue());
        cost.put("current_actual_cost", currentActual.doubleValue());
        cost.put("variance",
                ba != null && ba.getPlannedCost() != null
                        ? currentBudget.subtract(ba.getPlannedCost()).doubleValue() : null);
        wrapper.set("cost", cost);

        ObjectNode progress = objectMapper.createObjectNode();
        progress.put("baseline_percent_complete", ba == null ? null : ba.getPercentComplete());
        progress.put("current_percent_complete", activity.getPercentComplete());
        wrapper.set("progress", progress);

        Map<String, java.util.List<UUID>> links = new HashMap<>();
        links.put("baseline", List.of(baseline.getId()));
        links.put("activity", List.of(activity.getId()));
        ToolResult.attachLinks(wrapper, links);

        return ToolResult.ok(activity.getCode() + " vs " + baseline.getName()
                + " — finish slip " + daysBetween(baFinish, curFinish) + " day(s)", wrapper);
    }

    private Baseline pickBaseline(UUID projectId, JsonNode input) {
        String idStr = orNull(input.path("baseline_id").asText(null));
        if (idStr != null) {
            try {
                UUID id = UUID.fromString(idStr);
                Optional<Baseline> opt = baselineRepository.findById(id);
                if (opt.isPresent() && projectId.equals(opt.get().getProjectId())) return opt.get();
            } catch (IllegalArgumentException ignored) {}
        }
        List<Baseline> active = baselineRepository.findByProjectIdAndIsActiveTrue(projectId);
        if (!active.isEmpty()) return active.get(0);
        List<Baseline> all = baselineRepository.findByProjectId(projectId);
        return all.isEmpty() ? null : all.get(0);
    }

    private Activity resolveActivity(UUID projectId, JsonNode input) {
        String idStr = orNull(input.path("activity_id").asText(null));
        if (idStr != null) {
            try {
                UUID id = UUID.fromString(idStr);
                return activityRepository.findById(id).filter(a -> projectId.equals(a.getProjectId())).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        String code = orNull(input.path("activity_code").asText(null));
        if (code != null) {
            return activityRepository.findByProjectIdAndCode(projectId, code).orElse(null);
        }
        return null;
    }

    private ObjectNode toBaselineRow(Baseline b) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseline_id", b.getId() == null ? null : b.getId().toString());
        n.put("name", b.getName());
        n.put("description", b.getDescription());
        n.put("baseline_type", b.getBaselineType() == null ? null : b.getBaselineType().name());
        n.put("baseline_date", b.getBaselineDate() == null ? null : b.getBaselineDate().toString());
        n.put("is_active", b.getIsActive());
        n.put("total_activities", b.getTotalActivities());
        n.put("total_cost", b.getTotalCost() == null ? null : b.getTotalCost().doubleValue());
        n.put("project_duration", b.getProjectDuration());
        n.put("project_start_date", b.getProjectStartDate() == null ? null : b.getProjectStartDate().toString());
        n.put("project_finish_date", b.getProjectFinishDate() == null ? null : b.getProjectFinishDate().toString());
        return n;
    }

    private static Long daysBetween(LocalDate baseline, LocalDate current) {
        if (baseline == null || current == null) return null;
        return ChronoUnit.DAYS.between(baseline, current);
    }

    private static String orNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String orDefault(String s, String d) {
        return s == null || s.isBlank() ? d : s.trim();
    }

    private static class ScheduleDiff {
        UUID activityId;
        String code;
        String name;
        LocalDate baselineStart;
        LocalDate baselineFinish;
        LocalDate currentStart;
        LocalDate currentFinish;
        Long startSlipDays;
        Long finishSlipDays;
        Double percentComplete;
    }

    private static class CostDiff {
        UUID activityId;
        String code;
        String name;
        BigDecimal baselinePlanned = BigDecimal.ZERO;
        BigDecimal currentBudget = BigDecimal.ZERO;
        BigDecimal currentActual = BigDecimal.ZERO;
        BigDecimal variance = BigDecimal.ZERO;
    }
}
