package com.bipros.ai.activity.context;

import com.bipros.activity.domain.model.Activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flattens a project's existing activities into a compact text block for the
 * LLM prompt. Activities are scoped to WBS nodes via {@code wbsNodeId}; the
 * builder groups by WBS code so the model can reason about coverage per phase.
 *
 * <p>Bounded so the prompt stays small: cap by total count and per-WBS count,
 * with a truncation marker if exceeded.
 */
public final class ExistingActivitiesContextBuilder {

    private ExistingActivitiesContextBuilder() {}

    /**
     * @param activities all activities for the project
     * @param wbsIdToCode lookup: WBS node id → WBS code (for grouping)
     * @param maxTotal hard cap on total activities listed
     * @param maxPerWbs hard cap per WBS group
     * @return multi-line indented activity listing, or "" if no activities
     */
    public static String format(List<Activity> activities,
                                 Map<UUID, String> wbsIdToCode,
                                 int maxTotal,
                                 int maxPerWbs) {
        if (activities == null || activities.isEmpty()) return "";

        // Group by WBS code (fall back to "(unknown)" for orphans).
        Map<String, List<Activity>> byWbs = new HashMap<>();
        for (Activity a : activities) {
            String wbsCode = wbsIdToCode.getOrDefault(a.getWbsNodeId(), "(unknown)");
            byWbs.computeIfAbsent(wbsCode, k -> new ArrayList<>()).add(a);
        }

        StringBuilder out = new StringBuilder();
        out.append("Existing activities in this project (do NOT duplicate by code or by clearly equivalent name; ")
           .append("treat their codes as taken):\n");

        int emitted = 0;
        boolean truncated = false;
        // Sort WBS keys for deterministic output (helps prompt-cache reuse).
        List<String> wbsCodes = new ArrayList<>(byWbs.keySet());
        wbsCodes.sort(String::compareTo);
        for (String wbsCode : wbsCodes) {
            if (emitted >= maxTotal) {
                truncated = true;
                break;
            }
            List<Activity> group = byWbs.get(wbsCode);
            out.append("  WBS ").append(wbsCode).append(":\n");
            int perGroup = 0;
            for (Activity a : group) {
                if (perGroup >= maxPerWbs || emitted >= maxTotal) {
                    out.append("    [+ ").append(group.size() - perGroup).append(" more under this WBS]\n");
                    break;
                }
                out.append("    ").append(a.getCode()).append("  ").append(safeName(a.getName()));
                if (a.getOriginalDuration() != null) {
                    out.append(" (").append(a.getOriginalDuration()).append("d)");
                }
                out.append('\n');
                perGroup++;
                emitted++;
            }
        }
        if (truncated || activities.size() > emitted) {
            out.append("[Truncated: ").append(activities.size() - emitted)
               .append(" more activity(ies) not shown.]\n");
        }
        return out.toString();
    }

    private static String safeName(String name) {
        if (name == null) return "";
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
}
