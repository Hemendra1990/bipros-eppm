package com.bipros.cost.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure tests for the WBS grouping helpers behind getEvmByWbs (no Spring / DB). */
class CostServiceWbsGroupingTest {

    @Test
    void soleRoot_singleRoot_returnsIt() {
        UUID root = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, UUID> p = new HashMap<>();
        p.put(root, null); p.put(a, root); p.put(b, root);
        assertEquals(root, CostService.soleRootOf(p));
    }

    @Test
    void soleRoot_multipleRoots_returnsNull() {
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID();
        Map<UUID, UUID> p = new HashMap<>();
        p.put(r1, null); p.put(r2, null);
        assertNull(CostService.soleRootOf(p));
    }

    @Test
    void groupAncestor_singleRoot_groupsAtPhase() {
        UUID root = UUID.randomUUID(), phase = UUID.randomUUID(), leaf = UUID.randomUUID();
        Map<UUID, UUID> p = new HashMap<>();
        p.put(root, null); p.put(phase, root); p.put(leaf, phase);
        UUID sole = CostService.soleRootOf(p);
        assertEquals(phase, CostService.groupAncestor(leaf, p, sole));   // grandchild rolls up to its phase
        assertEquals(phase, CostService.groupAncestor(phase, p, sole));  // phase is its own group
        assertEquals(root, CostService.groupAncestor(root, p, sole));    // root-level work stays at the root
    }

    @Test
    void groupAncestor_multipleRoots_rollsToRoot() {
        UUID r1 = UUID.randomUUID(), r2 = UUID.randomUUID(), c1 = UUID.randomUUID();
        Map<UUID, UUID> p = new HashMap<>();
        p.put(r1, null); p.put(r2, null); p.put(c1, r1);
        UUID sole = CostService.soleRootOf(p);   // null — two roots
        assertNull(sole);
        assertEquals(r1, CostService.groupAncestor(c1, p, sole));
    }

    @Test
    void groupAncestor_unmapped_returnsNull() {
        UUID root = UUID.randomUUID();
        Map<UUID, UUID> p = new HashMap<>();
        p.put(root, null);
        assertNull(CostService.groupAncestor(null, p, root));               // no WBS link
        assertNull(CostService.groupAncestor(UUID.randomUUID(), p, root));  // orphan id not in the tree
    }
}
