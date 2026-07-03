package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryFilterTest {

    private static class StubTool implements Tool {
        private final String n;
        private final Set<String> roles;
        StubTool(String n, Set<String> roles) { this.n = n; this.roles = roles; }
        @Override public String name() { return n; }
        @Override public String description() { return n + " desc"; }
        @Override public JsonNode inputSchema() { return JsonNodeFactory.instance.objectNode(); }
        @Override public ToolResult execute(JsonNode i, AiContext c) { return ToolResult.ok("ok"); }
        @Override public Set<String> allowedRoles() { return roles; }
    }

    @Test
    void unrestrictedTool_visibleToEveryProfile() {
        Tool open = new StubTool("open", Set.of());
        ToolRegistry r = new ToolRegistry(List.of(open));
        assertEquals(1, r.toolsForProfile("SITE_MANAGER").size());
        assertEquals(1, r.toolsForProfile("QC_MANAGER").size());
        assertEquals(1, r.toolsForProfile(null).size());
    }

    @Test
    void restrictedTool_filtersByProfile() {
        Tool pmOnly = new StubTool("pm_only", Set.of("PROJECT_MANAGER"));
        Tool open = new StubTool("open", Set.of());
        ToolRegistry r = new ToolRegistry(List.of(pmOnly, open));

        assertEquals(2, r.toolsForProfile("PROJECT_MANAGER").size());
        assertEquals(1, r.toolsForProfile("SITE_MANAGER").size());
        assertEquals("open", r.toolsForProfile("SITE_MANAGER").get(0).name());
    }

    @Test
    void systemAdmin_seesEveryTool() {
        Tool pmOnly = new StubTool("pm_only", Set.of("PROJECT_MANAGER"));
        Tool qcOnly = new StubTool("qc_only", Set.of("QC_MANAGER"));
        ToolRegistry r = new ToolRegistry(List.of(pmOnly, qcOnly));

        assertEquals(2, r.toolsForProfile("SYSTEM_ADMIN").size());
    }

    @Test
    void isAllowed_returnsFalseForDisallowedProfile() {
        Tool qcOnly = new StubTool("qc_only", Set.of("QC_MANAGER"));
        ToolRegistry r = new ToolRegistry(List.of(qcOnly));

        assertTrue(r.isAllowed("qc_only", "QC_MANAGER"));
        assertTrue(r.isAllowed("qc_only", "SYSTEM_ADMIN"));
        assertFalse(r.isAllowed("qc_only", "SITE_MANAGER"));
    }

    @Test
    void isAllowed_returnsTrueForUnknownTool() {
        ToolRegistry r = new ToolRegistry(List.of());
        assertTrue(r.isAllowed("nonexistent", "SITE_MANAGER"));
    }

    @Test
    void adminRole_seesEveryTool_evenWithNullProfile() {
        // Admin authenticates with a username principal, so profileCode can be null.
        // The ADMIN role must still act as a tool-visibility superuser, mirroring the
        // execution-time project-scope bypass — otherwise admin loses list_issues,
        // analyze_risk, and every other role-restricted tool.
        Tool pmOnly = new StubTool("pm_only", Set.of("PROJECT_MANAGER"));
        Tool qcOnly = new StubTool("qc_only", Set.of("QC_MANAGER"));
        ToolRegistry r = new ToolRegistry(List.of(pmOnly, qcOnly));

        assertEquals(2, r.toolsForProfile(null, "ADMIN").size());
        assertTrue(r.isAllowed("pm_only", null, "ADMIN"));
        assertTrue(r.isAllowed("qc_only", null, "ADMIN"));
    }

    @Test
    void nonAdminRole_stillFiltersByProfile() {
        Tool pmOnly = new StubTool("pm_only", Set.of("PROJECT_MANAGER"));
        Tool open = new StubTool("open", Set.of());
        ToolRegistry r = new ToolRegistry(List.of(pmOnly, open));

        assertEquals(1, r.toolsForProfile("SITE_MANAGER", "USER").size());
        assertFalse(r.isAllowed("pm_only", "SITE_MANAGER", "USER"));
    }
}
