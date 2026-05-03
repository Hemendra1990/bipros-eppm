package com.bipros.ai.wbs;

import com.bipros.ai.wbs.dto.WbsAiNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic safety net that takes whatever shape of WBS tree an LLM
 * returned (perfectly nested, fully flat, leaves-only with hierarchical codes,
 * or anything in between) and rebuilds a single, consistent hierarchy.
 *
 * <p>Three-pass algorithm:
 * <ol>
 *   <li><b>Flatten</b> — collect every node (top-level + transitively nested)
 *       into a {@link LinkedHashMap} keyed by code, preserving first-occurrence
 *       order. Stale {@code children[]} references are stripped; we rethread
 *       from scratch.</li>
 *   <li><b>Synthesize missing intermediates</b> — for every node with a dotted
 *       code, walk up its segment-prefixes. When a prefix is missing from the
 *       batch <i>and</i> from the existing project WBS, synthesize a stub
 *       parent so the leaf has somewhere to attach. This is what lets the
 *       common LLM failure mode "emit only leaves coded {@code 1.2.3.4} and
 *       skip the {@code 1, 1.2, 1.2.3} parents" produce a proper tree.</li>
 *   <li><b>Re-thread</b> — for each node, compute its parent in priority order
 *       ({@code parentCode} that resolves → dotted-prefix walk-up that finds
 *       a batch node → dotted-prefix walk-up that finds an existing project
 *       node → root). Sort siblings by natural code order.</li>
 * </ol>
 *
 * <p>Idempotent: a perfectly nested input produces an identical output.
 *
 * <p>{@code parentCode} is null on output for nodes nested under another batch
 * node ({@code children[]} is the source of truth). For nodes that remain at
 * the top level, {@code parentCode} is set to the closest matching existing
 * project code (so the apply step can graft them under existing project
 * nodes), or preserved as the model emitted it.
 */
public final class WbsHierarchyReconstructor {

    private WbsHierarchyReconstructor() {}

    /** Convenience overload — no existing-project context. */
    public static List<WbsAiNode> rehydrate(List<WbsAiNode> nodes) {
        return rehydrate(nodes, Set.of());
    }

    public static List<WbsAiNode> rehydrate(List<WbsAiNode> nodes, Set<String> existingProjectCodes) {
        if (nodes == null || nodes.isEmpty()) return List.of();
        if (existingProjectCodes == null) existingProjectCodes = Set.of();

        // Pass 1: Flatten. Strip stale children[] — we'll rethread from scratch.
        LinkedHashMap<String, WbsAiNode> flat = new LinkedHashMap<>();
        for (WbsAiNode n : nodes) flatten(n, flat);

        // Pass 2: Synthesize missing intermediate parents so leaves-only output
        // ("RES-0012.3.1.1" with no "RES-0012.3.1", no "RES-0012.3", no "RES-0012")
        // can still nest into a tree. Stops walking up when an in-batch or
        // existing-project node is found — never duplicates an existing code.
        Set<String> synthesized = new HashSet<>();
        synthesizeMissingIntermediates(flat, existingProjectCodes, synthesized);

        // Pass 3: For each node, compute its parent code (or null for roots).
        Map<String, String> parentOf = new HashMap<>();
        for (WbsAiNode n : flat.values()) {
            String pc = computeParentCode(n, flat, existingProjectCodes);
            if (pc != null && pc.equals(n.code())) pc = null;
            parentOf.put(n.code(), pc);
        }

        // Group children by their (in-batch) parent code; root nodes keep a
        // pointer to the existing-project parent (if any) via parentCode on output.
        Map<String, List<WbsAiNode>> childrenOf = new HashMap<>();
        List<WbsAiNode> roots = new ArrayList<>();
        for (WbsAiNode n : flat.values()) {
            String parent = parentOf.get(n.code());
            if (parent == null || existingProjectCodes.contains(parent)) {
                roots.add(n);
            } else {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(n);
            }
        }

        roots.sort(WbsHierarchyReconstructor::compareCode);
        Map<String, String> parentOfFinal = parentOf;
        Set<String> existingFinal = existingProjectCodes;
        return roots.stream()
                .map(r -> rebuild(r, childrenOf, parentOfFinal, existingFinal, /* isRoot */ true))
                .collect(Collectors.toList());
    }

    private static void flatten(WbsAiNode n, LinkedHashMap<String, WbsAiNode> flat) {
        if (n == null || n.code() == null || n.code().isBlank()) return;
        flat.putIfAbsent(n.code(),
                new WbsAiNode(n.code(), n.name(), n.description(), n.parentCode(), null));
        if (n.children() != null) {
            for (WbsAiNode c : n.children()) flatten(c, flat);
        }
    }

    private static void synthesizeMissingIntermediates(LinkedHashMap<String, WbsAiNode> flat,
                                                       Set<String> existingCodes,
                                                       Set<String> synthesized) {
        // Snapshot — flat is mutated as we discover missing parents.
        List<String> codes = new ArrayList<>(flat.keySet());
        for (String code : codes) {
            WbsAiNode n = flat.get(code);
            // Skip synthesis when the node's explicit parentCode already
            // resolves: it points to a real parent, so dotted-prefix ancestors
            // don't need to be invented.
            String pc = n.parentCode();
            if (pc != null && !pc.isBlank() && !pc.equals(code)
                    && (flat.containsKey(pc) || existingCodes.contains(pc))) {
                continue;
            }
            int dot = code.lastIndexOf('.');
            while (dot > 0) {
                String prefix = code.substring(0, dot);
                if (flat.containsKey(prefix)) break;
                if (existingCodes.contains(prefix)) break;
                flat.put(prefix, new WbsAiNode(prefix, synthesizeName(prefix), null, null, null));
                synthesized.add(prefix);
                dot = prefix.lastIndexOf('.');
            }
        }
    }

    /**
     * Best-effort name for a synthesized intermediate. The full code is always
     * preserved; the name is what the user sees in the preview tree.
     *
     * <ul>
     *   <li>{@code "RES-0012"} (no dot) → {@code "RES-0012"}</li>
     *   <li>{@code "RES-0012.3"} → {@code "Section 3"}</li>
     *   <li>{@code "RES-0012.3.1"} → {@code "Section 3.1"}</li>
     *   <li>{@code "1.2"} → {@code "Section 1.2"}</li>
     * </ul>
     */
    static String synthesizeName(String code) {
        int firstDot = code.indexOf('.');
        if (firstDot < 0) return code;
        return "Section " + code.substring(firstDot + 1);
    }

    private static String computeParentCode(WbsAiNode n,
                                            Map<String, WbsAiNode> flat,
                                            Set<String> existingCodes) {
        // Layer 1: explicit parentCode if it points to another batch or existing node.
        String pc = n.parentCode();
        if (pc != null && !pc.isBlank() && !pc.equals(n.code())
                && (flat.containsKey(pc) || existingCodes.contains(pc))) {
            return pc;
        }
        // Layer 2: walk up dotted-segment prefixes — first batch hit wins,
        // then first existing-project hit.
        String code = n.code();
        int dot = code.lastIndexOf('.');
        while (dot > 0) {
            String prefix = code.substring(0, dot);
            if (flat.containsKey(prefix)) return prefix;
            if (existingCodes.contains(prefix)) return prefix;
            dot = prefix.lastIndexOf('.');
        }
        return null;
    }

    private static WbsAiNode rebuild(WbsAiNode node,
                                     Map<String, List<WbsAiNode>> childrenOf,
                                     Map<String, String> parentOf,
                                     Set<String> existingCodes,
                                     boolean isRoot) {
        // For nodes nested under another batch node, children[] is the truth —
        // null out parentCode. For top-level nodes, set parentCode to the
        // matching existing-project code (if any) so apply can graft there.
        String parentCode;
        if (isRoot) {
            String computedParent = parentOf.get(node.code());
            if (computedParent != null && existingCodes.contains(computedParent)) {
                parentCode = computedParent;
            } else {
                parentCode = node.parentCode();
            }
        } else {
            parentCode = null;
        }

        List<WbsAiNode> kids = childrenOf.get(node.code());
        if (kids == null || kids.isEmpty()) {
            return new WbsAiNode(node.code(), node.name(), node.description(), parentCode, List.of());
        }
        kids.sort(WbsHierarchyReconstructor::compareCode);
        List<WbsAiNode> rebuilt = kids.stream()
                .map(k -> rebuild(k, childrenOf, parentOf, existingCodes, /* isRoot */ false))
                .collect(Collectors.toList());
        return new WbsAiNode(node.code(), node.name(), node.description(), parentCode, rebuilt);
    }

    /** Natural compare of dotted codes: segment-by-segment, numeric where possible. */
    static int compareCode(WbsAiNode a, WbsAiNode b) {
        String[] sa = a.code().split("\\.");
        String[] sb = b.code().split("\\.");
        int n = Math.min(sa.length, sb.length);
        for (int i = 0; i < n; i++) {
            int cmp = compareSegment(sa[i], sb[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(sa.length, sb.length);
    }

    private static int compareSegment(String a, String b) {
        if (isNumeric(a) && isNumeric(b)) {
            try {
                return Long.compare(Long.parseLong(a), Long.parseLong(b));
            } catch (NumberFormatException ignored) {
                // fall through to string compare
            }
        }
        return a.compareToIgnoreCase(b);
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
