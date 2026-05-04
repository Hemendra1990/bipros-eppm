package com.bipros.ai.wbs.context;

import com.bipros.project.domain.model.WbsNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flattens a project's existing WBS into a compact, hierarchical text block
 * that can be injected into the LLM prompt. The model sees what already
 * exists and is instructed to avoid producing duplicates and to reference
 * existing codes as {@code parentCode} when extracting new sub-packages.
 *
 * <p>Output is bounded so the prompt stays small: cap by depth ({@code maxLevels})
 * and node count ({@code maxNodes}), with a truncation marker when exceeded.
 */
public final class ExistingWbsContextBuilder {

    private ExistingWbsContextBuilder() {}

    /**
     * @return a multi-line indented WBS listing, or {@code ""} if there are
     *         no existing nodes (caller can decide whether to include a
     *         "no existing WBS" note in the prompt).
     */
    public static String format(List<WbsNode> nodes, int maxLevels, int maxNodes) {
        if (nodes == null || nodes.isEmpty()) return "";

        Map<UUID, List<WbsNode>> byParent = new HashMap<>();
        List<WbsNode> roots = new ArrayList<>();
        for (WbsNode n : nodes) {
            if (n.getParentId() == null) {
                roots.add(n);
            } else {
                byParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("Existing WBS in this project (do NOT duplicate; reference existing codes ")
           .append("as parentCode for any new children you extract):\n");

        int[] emitted = {0};
        boolean[] truncated = {false};
        for (WbsNode r : roots) {
            walk(out, r, byParent, 0, maxLevels, maxNodes, emitted, truncated);
            if (truncated[0]) break;
        }

        int total = nodes.size();
        if (truncated[0] || total > emitted[0]) {
            out.append("[Truncated: ").append(total - emitted[0])
               .append(" more node(s) at deeper levels not shown.]\n");
        }
        return out.toString();
    }

    private static void walk(StringBuilder out,
                              WbsNode node,
                              Map<UUID, List<WbsNode>> byParent,
                              int depth,
                              int maxLevels,
                              int maxNodes,
                              int[] emitted,
                              boolean[] truncated) {
        if (emitted[0] >= maxNodes) {
            truncated[0] = true;
            return;
        }
        for (int i = 0; i < depth; i++) out.append("  ");
        out.append(node.getCode()).append("  ").append(safeName(node.getName())).append('\n');
        emitted[0]++;

        if (depth + 1 >= maxLevels) return;
        List<WbsNode> children = byParent.get(node.getId());
        if (children == null) return;
        for (WbsNode c : children) {
            walk(out, c, byParent, depth + 1, maxLevels, maxNodes, emitted, truncated);
            if (truncated[0]) return;
        }
    }

    private static String safeName(String name) {
        if (name == null) return "";
        // Strip control chars (CR/LF/TAB/etc) so each node stays on its own line in the
        // prompt — also a defensive measure against prompt-injection through node names.
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
}
