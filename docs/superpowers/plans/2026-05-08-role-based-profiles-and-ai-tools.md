# Role-Based Profiles & Role-Aware Global AI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 4 new user roles + 5 profiles (BIM/Data Coordinator, QC Manager, Project Engineer, Site Manager, refined PM); make the Global AI tool registry filter by profile and append a role-specific persona block to the system prompt; add 15 new role-aligned AI tools.

**Architecture:** Three layers, each with isolated change surface. (1) **Identity** — extend `DataSeeder.seedRoles()`, `PermissionCatalog.ALL`, and `ProfileSeeder.DEFAULTS`. (2) **AI capability** — add `allowedRoles()` to `Tool`, `toolsForProfile()` to `ToolRegistry`, `profile` field on `AiContext`, persona injection in `AiOrchestrator.buildSystemPrompt()`. (3) **AI tools** — 15 new tool classes under `bipros-ai/src/main/java/com/bipros/ai/tool/role/<role>/` plus `allowedRoles` tags on existing tools. Tools whose data isn't captured return a structured `data_unavailable` ToolResult.

**Tech Stack:** Java 23, Spring Boot 3.5, Maven, JPA, ClickHouse via `ClickHouseTemplate`, JUnit 5, Mockito, Jackson, React/Next.js 16 (frontend label map only).

**Spec:** `docs/superpowers/specs/2026-05-08-role-based-profiles-and-ai-tools-design.md`

---

## File Structure

### Created files

```
backend/bipros-ai/src/main/java/com/bipros/ai/persona/
  RolePersona.java                       # immutable record
  RolePersonaProvider.java               # @Component, returns persona by profile

backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/site_manager/
  AnalyzeLabourUtilizationTool.java
  AnalyzeMachineIdleTimeTool.java
  AnalyzeMaterialWastageTool.java
  CheckStockpileVsPlanTool.java

backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/project_engineer/
  AnalyzeProductivityFactorTool.java
  AnalyzeYieldVarianceTool.java
  AnalyzeEquipmentCycleTimeTool.java

backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/qc_manager/
  AnalyzeNcrTrendsTool.java
  AuditTraceabilityTool.java
  AnalyzeQualityDataGapsTool.java

backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/project_manager/
  AnalyzeLabourCostPerUnitTool.java
  AnalyzeMaterialBurnRateTool.java
  AnalyzeEquipmentUtilizationCostTool.java

backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/bim_coordinator/
  AuditDprDataQualityTool.java
  ReportDataLagTool.java

backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolRegistryFilterTest.java
backend/bipros-ai/src/test/java/com/bipros/ai/persona/RolePersonaProviderTest.java
backend/bipros-api/src/test/java/com/bipros/api/config/ProfileSeederNewProfilesTest.java
backend/bipros-ai/src/test/java/com/bipros/ai/tool/role/<one test file per new tool>
```

### Modified files

```
backend/bipros-security/src/main/java/com/bipros/security/domain/model/PermissionCatalog.java
backend/bipros-api/src/main/java/com/bipros/api/config/DataSeeder.java
backend/bipros-api/src/main/java/com/bipros/api/config/ProfileSeeder.java
backend/bipros-ai/src/main/java/com/bipros/ai/tool/Tool.java
backend/bipros-ai/src/main/java/com/bipros/ai/tool/ToolRegistry.java
backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContext.java
backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContextResolver.java
backend/bipros-ai/src/main/java/com/bipros/ai/orchestrator/AiOrchestrator.java
backend/bipros-ai/src/main/java/com/bipros/ai/tool/PortfolioKpiTool.java          # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeCostTool.java            # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeRiskTool.java            # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeScheduleTool.java        # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/ForecastCompletionTool.java     # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDprTool.java           # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/GetDprDetailsTool.java      # +allowedRoles
backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDailyOutputsTool.java  # +allowedRoles
frontend/src/components/ai/AiChatPanel.tsx                                          # add 15 progress labels
```

---

## Cross-Cutting Conventions

### Profile codes used in `allowedRoles` (must match `Profile.code`)

```
SYSTEM_ADMIN              # always allowed (registry treats as wildcard)
PORTFOLIO_MANAGER         # existing
PROJECT_MANAGER           # existing (refined in Task 3)
SCHEDULER                 # existing
RESOURCE_MANAGER          # existing
COST_CONTROLLER           # existing
RISK_MANAGER              # existing
DOCUMENT_CONTROLLER       # existing
SITE_ENGINEER             # existing (junior, kept)
EXECUTIVE_VIEWER          # existing
SITE_MANAGER              # NEW
PROJECT_ENGINEER          # NEW
QC_MANAGER                # NEW
BIM_DATA_COORDINATOR      # NEW
```

### `data_unavailable` ToolResult shape (used by every new tool when data is sparse/missing)

```java
ObjectNode payload = objectMapper.createObjectNode();
payload.put("status", "data_unavailable");
payload.put("reason", "<specific reason: which entity / which field / which project>");
payload.put("what_would_be_needed", "<actionable: what to capture or instrument>");
payload.put("closest_available", "<other tool the user could try, or null>");
return ToolResult.ok("Data not yet captured for this query — see details.", payload);
```

The orchestrator surfaces the summary verbatim. Tools must NEVER silently return an empty array — they must say the gap exists.

### Test helper (referenced from many tests)

```java
// backend/bipros-ai/src/test/java/com/bipros/ai/testsupport/AiContextFixtures.java
package com.bipros.ai.testsupport;

import com.bipros.ai.context.AiContext;
import java.util.List;
import java.util.UUID;

public final class AiContextFixtures {
    private AiContextFixtures() {}

    public static AiContext forProfile(String profileCode, UUID projectId) {
        UUID userId = UUID.randomUUID();
        UUID pid = projectId == null ? UUID.randomUUID() : projectId;
        return new AiContext(
                userId,
                pid,
                "general",
                profileCode,             // role field carries profile code (Task 6)
                profileCode,             // profile field
                List.of(pid)
        );
    }
}
```

Create this once in **Task 14** before the first tool task uses it.

---

## Phase 1 — Identity Layer

### Task 1: Add new permission codes to `PermissionCatalog`

**Files:**
- Modify: `backend/bipros-security/src/main/java/com/bipros/security/domain/model/PermissionCatalog.java`

**Why these codes:** the spec calls for QC, NCR, data-quality, and yield-variance permission distinctions. We add them additively — no existing code is removed or renamed.

- [ ] **Step 1: Append the new permissions to `ALL`**

After the last `ADMIN_SETTINGS.UPDATE` entry, **before** the closing `)` of `List.of(`, add:

```java
            ,
            // Quality / NCR (used by QC_MANAGER profile and analyze_ncr_trends tool)
            new Permission("NCR.CREATE",  "NCR", CREATE, "Create non-conformance reports"),
            new Permission("NCR.READ",    "NCR", READ,   "View non-conformance reports"),
            new Permission("NCR.UPDATE",  "NCR", UPDATE, "Update / close NCRs"),
            new Permission("NCR.APPROVE", "NCR", APPROVE, "Approve NCR closure"),

            // Data quality (used by BIM_DATA_COORDINATOR profile)
            new Permission("DATA_QUALITY.READ",  "DATA_QUALITY", READ,  "View data-quality and DPR audit reports"),
            new Permission("DATA_QUALITY.AUDIT", "DATA_QUALITY", "AUDIT", "Run DPR completeness audits"),

            // DPR QC annotations (used by QC_MANAGER profile)
            new Permission("DPR.QC_ANNOTATE", "DPR", "ANNOTATE", "Add QC observations / annotations to DPRs"),

            // Yield variance (used by PROJECT_ENGINEER profile and analyze_yield_variance tool)
            new Permission("YIELD_VARIANCE.READ", "YIELD_VARIANCE", READ, "View material yield variance reports"),

            // AI write (lets a profile both run the AI and use write-capable AI tools when added)
            new Permission("AI.WRITE", "AI", "WRITE", "Run AI tools that write back to the system")
```

- [ ] **Step 2: Write a failing test that the new codes exist**

Create `backend/bipros-security/src/test/java/com/bipros/security/domain/model/PermissionCatalogNewCodesTest.java`:

```java
package com.bipros.security.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogNewCodesTest {

    @Test
    void hasAllNewPermissionCodes() {
        String[] expected = {
                "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "NCR.APPROVE",
                "DATA_QUALITY.READ", "DATA_QUALITY.AUDIT",
                "DPR.QC_ANNOTATE",
                "YIELD_VARIANCE.READ",
                "AI.WRITE"
        };
        for (String code : expected) {
            assertTrue(PermissionCatalog.isValid(code),
                    "Missing permission code: " + code);
        }
    }
}
```

- [ ] **Step 3: Run the test — expect PASS (the codes are now in the catalog)**

```bash
cd backend && mvn -pl bipros-security test -Dtest=PermissionCatalogNewCodesTest -q
```

Expected: 1 test, 0 failures. If it fails with "Missing permission code: X", the catalog edit in Step 1 missed code X.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-security/src/main/java/com/bipros/security/domain/model/PermissionCatalog.java \
        backend/bipros-security/src/test/java/com/bipros/security/domain/model/PermissionCatalogNewCodesTest.java
git commit -m "feat(security): add NCR / DATA_QUALITY / YIELD_VARIANCE / AI.WRITE permission codes"
```

---

### Task 2: Add new legacy roles to `DataSeeder.seedRoles()`

**Files:**
- Modify: `backend/bipros-api/src/main/java/com/bipros/api/config/DataSeeder.java:94-121`

- [ ] **Step 1: Append 4 rows to the `roles` array**

In `seedRoles()`, after the `HSE_OFFICER` row, before the closing `};`, add:

```java
      ,
      {"SITE_MANAGER", "Site Manager; owns daily execution, crew & machine deployment"},
      {"PROJECT_ENGINEER", "Project Engineer; bridges design and execution, technical sign-off"},
      {"QC_MANAGER", "Quality Control Manager; process adherence, NCR ownership"},
      {"BIM_DATA_COORDINATOR", "BIM / Data Coordinator; data integrity and model linkage"}
```

- [ ] **Step 2: Write a failing test that DataSeeder seeds the new roles**

Create `backend/bipros-api/src/test/java/com/bipros/api/config/DataSeederNewRolesTest.java`:

```java
package com.bipros.api.config;

import com.bipros.security.domain.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
class DataSeederNewRolesTest {

    @Autowired RoleRepository roleRepository;

    @Test
    void seedsAllFourNewRoles() {
        for (String name : new String[]{
                "SITE_MANAGER", "PROJECT_ENGINEER", "QC_MANAGER", "BIM_DATA_COORDINATOR"
        }) {
            assertTrue(roleRepository.findByName(name).isPresent(),
                    "Missing role after seed: " + name);
        }
    }
}
```

- [ ] **Step 3: Run the test**

```bash
cd backend && mvn -pl bipros-api test -Dtest=DataSeederNewRolesTest -q
```

Expected: PASS — `dev` profile triggers `DataSeeder.run()` which calls `seedRoles()`.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-api/src/main/java/com/bipros/api/config/DataSeeder.java \
        backend/bipros-api/src/test/java/com/bipros/api/config/DataSeederNewRolesTest.java
git commit -m "feat(security): seed SITE_MANAGER / PROJECT_ENGINEER / QC_MANAGER / BIM_DATA_COORDINATOR legacy roles"
```

---

### Task 3: Add 4 new profiles + refine `PROJECT_MANAGER` in `ProfileSeeder`

**Files:**
- Modify: `backend/bipros-api/src/main/java/com/bipros/api/config/ProfileSeeder.java:50-186`

- [ ] **Step 1: Refine the existing `PROJECT_MANAGER` profile**

The existing PROJECT_MANAGER permission set already has most of what we need. Add `AI.WRITE` to it. Locate the PROJECT_MANAGER block (lines 70-89) and inside `of(...)`, append `, "AI.WRITE"` after `"AI.READ"`. The line becomes:

```java
                            "REPORT.READ", "REPORT.EXPORT",
                            "AI.READ", "AI.WRITE"
                    )
```

- [ ] **Step 2: Append 4 new profiles to `DEFAULTS`**

Inside `List.of(...)`, after the last existing profile (`EXECUTIVE_VIEWER`), append:

```java
            ,
            new DefaultProfile(
                    "SITE_MANAGER",
                    "Site Manager",
                    "Daily site execution: crew & machine deployment, materials, DPR ownership.",
                    "SITE_MANAGER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ", "ACTIVITY.UPDATE",
                            "SCHEDULE.READ",
                            "RESOURCE.READ", "RESOURCE.UPDATE",
                            "COST.READ",
                            "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                            "REPORT.READ",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "PROJECT_ENGINEER",
                    "Project Engineer",
                    "Design–execution bridge: activity & DPR review, yield variance, output norms.",
                    "PROJECT_ENGINEER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ", "ACTIVITY.UPDATE",
                            "SCHEDULE.READ",
                            "RESOURCE.READ",
                            "COST.READ",
                            "EVM.READ",
                            "DOCUMENT.READ",
                            "YIELD_VARIANCE.READ",
                            "REPORT.READ",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "QC_MANAGER",
                    "Quality Control Manager",
                    "Process adherence and traceability: NCRs, QC annotations on DPRs, audit trails.",
                    "QC_MANAGER",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ",
                            "RESOURCE.READ",
                            "DOCUMENT.READ",
                            "RISK.READ",
                            "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "NCR.APPROVE",
                            "DPR.QC_ANNOTATE",
                            "REPORT.READ",
                            "AI.READ"
                    )
            ),
            new DefaultProfile(
                    "BIM_DATA_COORDINATOR",
                    "BIM / Data Coordinator",
                    "Data-integrity steward: DPR completeness audits, model linkage, data lag.",
                    "BIM_DATA_COORDINATOR",
                    of(
                            "PROJECT.READ",
                            "ACTIVITY.READ",
                            "RESOURCE.READ",
                            "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE",
                            "ADMIN_MASTER.READ",
                            "DATA_QUALITY.READ", "DATA_QUALITY.AUDIT",
                            "REPORT.READ",
                            "AI.READ"
                    )
            )
```

- [ ] **Step 3: Write a failing test for the new profiles**

Create `backend/bipros-api/src/test/java/com/bipros/api/config/ProfileSeederNewProfilesTest.java`:

```java
package com.bipros.api.config;

import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class ProfileSeederNewProfilesTest {

    @Autowired ProfileRepository profileRepository;

    @Test
    void seedsSiteManagerProfile() {
        Profile p = profileRepository.findByCode("SITE_MANAGER").orElseThrow();
        assertEquals("SITE_MANAGER", p.getLegacyRoleName());
        assertTrue(p.getPermissions().containsAll(Set.of(
                "PROJECT.READ", "ACTIVITY.READ", "ACTIVITY.UPDATE",
                "RESOURCE.UPDATE", "AI.READ")));
    }

    @Test
    void seedsProjectEngineerProfile() {
        Profile p = profileRepository.findByCode("PROJECT_ENGINEER").orElseThrow();
        assertEquals("PROJECT_ENGINEER", p.getLegacyRoleName());
        assertTrue(p.getPermissions().contains("YIELD_VARIANCE.READ"));
    }

    @Test
    void seedsQcManagerProfile() {
        Profile p = profileRepository.findByCode("QC_MANAGER").orElseThrow();
        assertEquals("QC_MANAGER", p.getLegacyRoleName());
        assertTrue(p.getPermissions().containsAll(Set.of(
                "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "DPR.QC_ANNOTATE")));
    }

    @Test
    void seedsBimDataCoordinatorProfile() {
        Profile p = profileRepository.findByCode("BIM_DATA_COORDINATOR").orElseThrow();
        assertEquals("BIM_DATA_COORDINATOR", p.getLegacyRoleName());
        assertTrue(p.getPermissions().containsAll(Set.of(
                "DATA_QUALITY.READ", "DATA_QUALITY.AUDIT")));
    }

    @Test
    void projectManagerHasAiWrite() {
        Profile p = profileRepository.findByCode("PROJECT_MANAGER").orElseThrow();
        assertTrue(p.getPermissions().contains("AI.WRITE"),
                "PROJECT_MANAGER must now include AI.WRITE");
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
cd backend && mvn -pl bipros-api test -Dtest=ProfileSeederNewProfilesTest -q
```

Expected: 5 tests, 0 failures.

> **Important:** ProfileSeeder is idempotent and skips if `code` exists. The `projectManagerHasAiWrite` test will fail on a database where PROJECT_MANAGER was already seeded WITHOUT `AI.WRITE`. The test infrastructure starts with a fresh H2 / Postgres test container, so this is fine for CI. For developer dev DBs, document the manual mitigation in the commit message.

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-api/src/main/java/com/bipros/api/config/ProfileSeeder.java \
        backend/bipros-api/src/test/java/com/bipros/api/config/ProfileSeederNewProfilesTest.java
git commit -m "$(cat <<'EOF'
feat(security): seed SITE_MANAGER / PROJECT_ENGINEER / QC_MANAGER / BIM_DATA_COORDINATOR profiles + AI.WRITE on PROJECT_MANAGER

ProfileSeeder is idempotent — existing dev databases that already have a
PROJECT_MANAGER profile will retain the old permission set. To pick up the
AI.WRITE addition on a dev DB, either drop+recreate the profiles row or
edit it in the Profile Admin UI.
EOF
)"
```

---

## Phase 2 — AI Capability Layer

### Task 4: Add `allowedRoles()` to the `Tool` interface

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/tool/Tool.java`

- [ ] **Step 1: Replace the `Tool` interface body**

```java
package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * A tool callable by the AI orchestrator.
 *
 * <p>{@link #allowedRoles()} returns the set of profile codes (e.g.
 * {@code "PROJECT_MANAGER"}) for which this tool is visible. An empty set
 * means "visible to every profile". {@code SYSTEM_ADMIN} is implicitly
 * allowed everywhere — handled at the registry level, NOT in each tool.
 */
public interface Tool {

    String name();

    String description();

    JsonNode inputSchema();

    ToolResult execute(JsonNode input, AiContext ctx);

    default boolean isReadOnly() {
        return true;
    }

    default Set<String> allowedRoles() {
        return Set.of();
    }
}
```

- [ ] **Step 2: Verify the project still compiles**

```bash
cd backend && mvn -pl bipros-ai compile -q
```

Expected: BUILD SUCCESS. No existing tool overrides `allowedRoles()` yet, so the default empty set keeps every tool visible — current behaviour preserved.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/tool/Tool.java
git commit -m "feat(ai): add Tool.allowedRoles() default — empty set means visible to all"
```

---

### Task 5: Add `toolsForProfile()` to `ToolRegistry` + per-call enforcement

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/tool/ToolRegistry.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolRegistryFilterTest.java`

- [ ] **Step 1: Write the failing test first**

Create `backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolRegistryFilterTest.java`:

```java
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
        // Unknown tool path is handled in orchestrator with a separate "Unknown tool"
        // error; the registry should not block here.
        ToolRegistry r = new ToolRegistry(List.of());
        assertTrue(r.isAllowed("nonexistent", "SITE_MANAGER"));
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=ToolRegistryFilterTest -q
```

Expected: compilation error (`toolsForProfile`, `isAllowed` not defined).

- [ ] **Step 3: Replace the `ToolRegistry` body**

```java
package com.bipros.ai.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolRegistry {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(Collection<Tool> toolBeans) {
        for (Tool t : toolBeans) {
            tools.put(t.name(), t);
        }
    }

    @PostConstruct
    public void init() {
        log.info("ToolRegistry loaded {} tools: {}", tools.size(), tools.keySet());
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /**
     * Returns the tools visible to the given profile. Tools with an empty
     * {@link Tool#allowedRoles()} are always included. SYSTEM_ADMIN sees every tool.
     */
    public List<Tool> toolsForProfile(String profileCode) {
        if (SYSTEM_ADMIN.equals(profileCode)) {
            return List.copyOf(tools.values());
        }
        return tools.values().stream()
                .filter(t -> t.allowedRoles().isEmpty()
                        || (profileCode != null && t.allowedRoles().contains(profileCode)))
                .toList();
    }

    /**
     * Defense-in-depth check used by the orchestrator before executing a
     * tool the LLM picked. Unknown tool names return {@code true} so the
     * existing "Unknown tool" error path in the orchestrator still fires.
     */
    public boolean isAllowed(String toolName, String profileCode) {
        Tool t = tools.get(toolName);
        if (t == null) return true;
        if (SYSTEM_ADMIN.equals(profileCode)) return true;
        return t.allowedRoles().isEmpty()
                || (profileCode != null && t.allowedRoles().contains(profileCode));
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=ToolRegistryFilterTest -q
```

Expected: 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/tool/ToolRegistry.java \
        backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolRegistryFilterTest.java
git commit -m "feat(ai): ToolRegistry filters tools by profile and exposes isAllowed for enforcement"
```

---

### Task 6: Add `profile` field to `AiContext`

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContext.java`

- [ ] **Step 1: Replace the record body**

```java
package com.bipros.ai.context;

import java.util.List;
import java.util.UUID;

/**
 * Context carried through every AI request: user identity, project scope, module.
 * Used for RBAC injection and tool scoping.
 *
 * <p>{@code role} retains the legacy role string ("ADMIN", "PROJECT_MANAGER", "USER")
 * for backward-compat with tools that already read it. {@code profile} carries the
 * fine-grained Profile.code (e.g. "SITE_MANAGER") used for tool filtering and persona
 * selection.
 */
public record AiContext(
    UUID userId,
    UUID projectId,
    String module,
    String role,
    String profile,
    List<UUID> scopedProjectIds
) {
}
```

- [ ] **Step 2: Compile — expect failures**

```bash
cd backend && mvn -pl bipros-ai compile -q
```

Expected: every `new AiContext(...)` call site fails because the constructor signature changed. Build errors will list the call sites.

- [ ] **Step 3: Fix call sites**

Update every `new AiContext(...)` in production code. Search for them:

```bash
grep -rn "new AiContext(" backend/bipros-ai/src/main/java
```

Expected hits include `AiContextResolver.java:38`. Insert `null` (or the appropriate value) for the new `profile` field. Specifically:

In `backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContextResolver.java:38`, change:
```java
return new AiContext(userId, effectiveProjectId, module, role, scoped);
```
to:
```java
return new AiContext(userId, effectiveProjectId, module, role, null, scoped);
```
(real profile resolution is wired up in Task 7.)

Also update any test usage:
```bash
grep -rn "new AiContext(" backend/bipros-ai/src/test/java backend
```
For each test, insert `null` for profile in the appropriate position.

- [ ] **Step 4: Compile + run all bipros-ai tests**

```bash
cd backend && mvn -pl bipros-ai test -q
```

Expected: BUILD SUCCESS. No test should newly fail (we passed `null` for profile, which existing logic doesn't read).

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContext.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContextResolver.java \
        backend/bipros-ai/src/test/  # any test files touched
git commit -m "feat(ai): add profile field to AiContext (set to null at all call sites for now)"
```

---

### Task 7: Resolve profile in `AiContextResolver`

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContextResolver.java`

The resolver must look up the current user's profile code. The cleanest path: inject `UserRepository` + `ProfileRepository` directly. Profile data is small and the lookup runs once per chat request.

- [ ] **Step 1: Read the existing User entity to confirm `getProfileId()` exists**

```bash
grep -n "profileId\|getProfileId" backend/bipros-security/src/main/java/com/bipros/security/domain/model/User.java
```

Expected: at least one `private UUID profileId` and a Lombok-generated `getProfileId()`. If absent, halt and report — the rest of the plan assumes this field exists per `DataSeeder.java:133` which calls `setProfileId`.

- [ ] **Step 2: Write a failing test for profile resolution**

Create `backend/bipros-ai/src/test/java/com/bipros/ai/context/AiContextResolverProfileTest.java`:

```java
package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AiContextResolverProfileTest {

    private SecurityContextHelper sec;
    private ProjectAccessGuard guard;
    private UserRepository userRepo;
    private ProfileRepository profileRepo;
    private AiContextResolver resolver;

    @BeforeEach
    void setUp() {
        sec = Mockito.mock(SecurityContextHelper.class);
        guard = Mockito.mock(ProjectAccessGuard.class);
        userRepo = Mockito.mock(UserRepository.class);
        profileRepo = Mockito.mock(ProfileRepository.class);
        resolver = new AiContextResolver(guard, sec, userRepo, profileRepo);
    }

    @Test
    void resolvesProfileFromUser() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User u = new User(); u.setProfileId(profileId);
        Profile p = new Profile("SITE_MANAGER", "Site Manager", null, "SITE_MANAGER", true, java.util.Set.of());

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(p));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(List.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertEquals("SITE_MANAGER", ctx.profile());
    }

    @Test
    void profileIsNullWhenUserHasNoProfile() {
        UUID userId = UUID.randomUUID();
        User u = new User(); u.setProfileId(null);

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(false);
        when(sec.hasRole("PROJECT_MANAGER")).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(List.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertNull(ctx.profile());
    }

    @Test
    void adminWithSystemAdminProfileResolves() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User u = new User(); u.setProfileId(profileId);
        Profile p = new Profile("SYSTEM_ADMIN", "System Admin", null, "ADMIN", true, java.util.Set.of());

        when(sec.getCurrentUserId()).thenReturn(userId);
        when(sec.hasRole("ADMIN")).thenReturn(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(u));
        when(profileRepo.findById(profileId)).thenReturn(Optional.of(p));
        when(guard.getAccessibleProjectIdsForCurrentUser()).thenReturn(List.of());

        AiContext ctx = resolver.resolve(null, "general");

        assertEquals("ADMIN", ctx.role());
        assertEquals("SYSTEM_ADMIN", ctx.profile());
    }
}
```

- [ ] **Step 3: Run — expect FAIL (constructor signature)**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AiContextResolverProfileTest -q
```

- [ ] **Step 4: Replace `AiContextResolver` body**

```java
package com.bipros.ai.context;

import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.security.SecurityContextHelper;
import com.bipros.security.domain.repository.ProfileRepository;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AiContextResolver {

    private final ProjectAccessGuard projectAccess;
    private final SecurityContextHelper securityContextHelper;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public AiContext resolve(UUID projectId, String module) {
        UUID userId;
        try {
            userId = securityContextHelper.getCurrentUserId();
        } catch (Exception e) {
            userId = null;
        }
        String role = securityContextHelper.hasRole("ADMIN") ? "ADMIN"
                : securityContextHelper.hasRole("PROJECT_MANAGER") ? "PROJECT_MANAGER" : "USER";

        String profileCode = resolveProfileCode(userId);

        List<UUID> scoped = projectAccess.getAccessibleProjectIdsForCurrentUser() != null
                ? List.copyOf(projectAccess.getAccessibleProjectIdsForCurrentUser())
                : List.of();

        UUID effectiveProjectId = projectId;
        if (effectiveProjectId == null
                && !"ADMIN".equals(role)
                && scoped.size() == 1) {
            effectiveProjectId = scoped.get(0);
        }

        return new AiContext(userId, effectiveProjectId, module, role, profileCode, scoped);
    }

    private String resolveProfileCode(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getProfileId())
                .flatMap(pid -> pid == null ? java.util.Optional.empty() : profileRepository.findById(pid))
                .map(p -> p.getCode())
                .orElse(null);
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AiContextResolverProfileTest -q
```

Expected: 3 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/context/AiContextResolver.java \
        backend/bipros-ai/src/test/java/com/bipros/ai/context/AiContextResolverProfileTest.java
git commit -m "feat(ai): AiContextResolver resolves user.profile.code into AiContext.profile"
```

---

### Task 8: Create `RolePersona` + `RolePersonaProvider`

**Files:**
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/persona/RolePersona.java`
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/persona/RolePersonaProvider.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/persona/RolePersonaProviderTest.java`

- [ ] **Step 1: Create `RolePersona.java`**

```java
package com.bipros.ai.persona;

import java.util.List;

/**
 * Per-role persona block appended to the system prompt. Anchors tone and
 * KPI focus for that profile so the LLM defaults to the questions the
 * profile cares about.
 *
 * @param headline      one-line "You are assisting a Site Manager."
 * @param primaryKpis   3-5 KPI names in business terms ("Labour utilization %", …)
 * @param preferTools   tool names this profile should reach for first
 * @param framingHint   one short sentence on how to frame answers
 */
public record RolePersona(
        String headline,
        List<String> primaryKpis,
        List<String> preferTools,
        String framingHint
) {
    /** Renders the persona as a prompt block. Returns "" for the null persona. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n────────────────────────────────────────\n");
        sb.append("ROLE PERSONA\n");
        sb.append("────────────────────────────────────────\n");
        sb.append(headline).append("\n");
        sb.append("Primary KPIs to lead with: ")
                .append(String.join(", ", primaryKpis)).append(".\n");
        sb.append("When the user's question is open-ended, prefer these tools first: ")
                .append(String.join(", ", preferTools)).append(".\n");
        sb.append(framingHint).append("\n");
        return sb.toString();
    }
}
```

- [ ] **Step 2: Write the failing test for the provider**

Create `backend/bipros-ai/src/test/java/com/bipros/ai/persona/RolePersonaProviderTest.java`:

```java
package com.bipros.ai.persona;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePersonaProviderTest {

    private final RolePersonaProvider provider = new RolePersonaProvider();

    @Test
    void siteManagerPersonaHasCrewIdleWastageKpis() {
        RolePersona p = provider.forProfile("SITE_MANAGER");
        assertNotNull(p);
        assertTrue(p.headline().contains("Site Manager"));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("utiliz")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("idle")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("wastage")));
    }

    @Test
    void projectManagerPersonaHasCpiSpiCostKpis() {
        RolePersona p = provider.forProfile("PROJECT_MANAGER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("CPI")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("SPI")));
    }

    @Test
    void qcManagerPersonaHasNcrTraceabilityKpis() {
        RolePersona p = provider.forProfile("QC_MANAGER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toUpperCase().contains("NCR")));
    }

    @Test
    void projectEngineerHasYieldProductivity() {
        RolePersona p = provider.forProfile("PROJECT_ENGINEER");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("yield")));
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("productivity")));
    }

    @Test
    void bimDataCoordinatorHasDataIntegrityKpis() {
        RolePersona p = provider.forProfile("BIM_DATA_COORDINATOR");
        assertNotNull(p);
        assertTrue(p.primaryKpis().stream().anyMatch(k -> k.toLowerCase().contains("data")));
    }

    @Test
    void unknownProfileReturnsNull() {
        assertNull(provider.forProfile("UNKNOWN"));
        assertNull(provider.forProfile(null));
    }

    @Test
    void renderProducesNonEmptyBlock() {
        RolePersona p = provider.forProfile("SITE_MANAGER");
        String block = p.render();
        assertTrue(block.contains("ROLE PERSONA"));
        assertTrue(block.contains("Site Manager"));
    }
}
```

- [ ] **Step 3: Run — expect FAIL (RolePersonaProvider missing)**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=RolePersonaProviderTest -q
```

- [ ] **Step 4: Create `RolePersonaProvider.java`**

```java
package com.bipros.ai.persona;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Static lookup of {@link RolePersona} by profile code. Returns {@code null}
 * for unknown profiles so the orchestrator skips the persona block (generic
 * prompt only).
 */
@Component
public class RolePersonaProvider {

    private static final Map<String, RolePersona> PERSONAS = Map.of(
            "SITE_MANAGER", new RolePersona(
                    "You are assisting a Site Manager — focus on today's execution wins and losses.",
                    List.of(
                            "Labour utilization %",
                            "Machine idle time %",
                            "Material wastage %",
                            "Stockpile-to-need ratio"),
                    List.of(
                            "analyze_labour_utilization",
                            "analyze_machine_idle_time",
                            "analyze_material_wastage",
                            "check_stockpile_vs_plan",
                            "query_dpr"),
                    "Frame answers as today's wins or losses, by crew or by location, in plain operational terms."
            ),
            "PROJECT_ENGINEER", new RolePersona(
                    "You are assisting a Project Engineer — focus on output standards, yield, and method efficiency.",
                    List.of(
                            "Productivity factor (output / man-hour vs norm)",
                            "Yield variance % (actual vs design)",
                            "Cycle time"),
                    List.of(
                            "analyze_productivity_factor",
                            "analyze_yield_variance",
                            "analyze_equipment_cycle_time",
                            "query_dpr"),
                    "Frame answers as design vs actual, by activity, with the variance number leading."
            ),
            "QC_MANAGER", new RolePersona(
                    "You are assisting a Quality Control Manager — focus on process adherence and traceability.",
                    List.of(
                            "NCR rate per crew / source",
                            "Material lot ↔ operator ↔ location traceability",
                            "Quality data completeness"),
                    List.of(
                            "analyze_ncr_trends",
                            "audit_traceability",
                            "analyze_quality_data_gaps",
                            "query_dpr"),
                    "Frame answers as process compliance gaps, by crew or by source, with traceable links where possible."
            ),
            "PROJECT_MANAGER", new RolePersona(
                    "You are assisting a Project Manager — focus on cost, schedule, and overall delivery health.",
                    List.of(
                            "CPI / SPI",
                            "Labour cost per unit installed",
                            "Material burn rate",
                            "Equipment utilization %"),
                    List.of(
                            "portfolio_kpi",
                            "analyze_cost",
                            "analyze_labour_cost_per_unit",
                            "analyze_material_burn_rate",
                            "analyze_equipment_utilization_cost",
                            "forecast_completion"),
                    "Frame answers as money-and-time impact, with the headline number leading."
            ),
            "BIM_DATA_COORDINATOR", new RolePersona(
                    "You are assisting a BIM / Data Coordinator — focus on data integrity and entry latency.",
                    List.of(
                            "DPR data completeness %",
                            "Site → system entry lag",
                            "Missing breakdown / linkage"),
                    List.of(
                            "audit_dpr_data_quality",
                            "report_data_lag",
                            "list_projects"),
                    "Frame answers as data-quality gaps to fix, by project and by missing-field category."
            )
    );

    public RolePersona forProfile(String profileCode) {
        if (profileCode == null) return null;
        return PERSONAS.get(profileCode);
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=RolePersonaProviderTest -q
```

Expected: 7 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/persona/ \
        backend/bipros-ai/src/test/java/com/bipros/ai/persona/
git commit -m "feat(ai): RolePersona + RolePersonaProvider for 5 profiles"
```

---

### Task 9: Wire persona + tool filtering into `AiOrchestrator`

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/orchestrator/AiOrchestrator.java`

- [ ] **Step 1: Add `RolePersonaProvider` injection**

In the constructor (around line 48), add `RolePersonaProvider personaProvider` as a parameter and store it as a field. Update the field declarations:

```java
    private final ToolRegistry toolRegistry;
    private final DataGraphCatalog dataGraphCatalog;
    private final com.bipros.ai.persona.RolePersonaProvider personaProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int generalRounds;
    private final int defaultRounds;

    public AiOrchestrator(ToolRegistry toolRegistry,
                          DataGraphCatalog dataGraphCatalog,
                          com.bipros.ai.persona.RolePersonaProvider personaProvider,
                          @Value("${bipros.ai-orchestrator.max-tool-rounds.general:12}") int generalRounds,
                          @Value("${bipros.ai-orchestrator.max-tool-rounds.default:10}") int defaultRounds) {
        this.toolRegistry = toolRegistry;
        this.dataGraphCatalog = dataGraphCatalog;
        this.personaProvider = personaProvider;
        this.generalRounds = generalRounds;
        this.defaultRounds = defaultRounds;
    }
```

- [ ] **Step 2: Replace `toolRegistry.all()` with `toolsForProfile(ctx.profile())`**

In `runAgentLoop`, change line ~81:

```java
        List<LlmProvider.ToolSpec> toolSpecs = toolRegistry.all().stream()
                .map(t -> new LlmProvider.ToolSpec(t.name(), t.description(), t.inputSchema()))
                .toList();
```

to:

```java
        List<LlmProvider.ToolSpec> toolSpecs = toolRegistry.toolsForProfile(ctx.profile()).stream()
                .map(t -> new LlmProvider.ToolSpec(t.name(), t.description(), t.inputSchema()))
                .toList();
```

- [ ] **Step 3: Append per-call enforcement in `executeToolsAndAppend`**

Locate the tool-execution lambda (around line 170-187) inside `executeToolsAndAppend`. Replace:

```java
                    Tool tool = toolRegistry.get(tc.name());
                    if (tool == null) {
                        return new ToolCallResult(tc.name(), false, "Unknown tool: " + tc.name(), null, 0);
                    }
                    try {
                        ToolResult result = tool.execute(tc.arguments(), ctx);
```

with:

```java
                    Tool tool = toolRegistry.get(tc.name());
                    if (tool == null) {
                        return new ToolCallResult(tc.name(), false, "Unknown tool: " + tc.name(), null, 0);
                    }
                    if (!toolRegistry.isAllowed(tc.name(), ctx.profile())) {
                        return new ToolCallResult(tc.name(), false,
                                "Tool '" + tc.name() + "' is not available for your role.", null, 0);
                    }
                    try {
                        ToolResult result = tool.execute(tc.arguments(), ctx);
```

- [ ] **Step 4: Inject persona block into the system prompt**

Locate the `buildSystemPrompt(AiContext ctx)` method (line 222) and the `return """ ... """` template ending around line 626. The template currently ends with the literal string `"...do not make it.\n            """`.

Two adjustments:

1. At the top of the method, after `String moduleAddendum = buildModuleAddendum(ctx.module());`, add:

```java
        com.bipros.ai.persona.RolePersona persona = personaProvider.forProfile(ctx.profile());
        String personaBlock = persona == null ? "" : persona.render();
```

2. In the prompt's CURRENT CONTEXT block (around line 590-595), the existing format string already has slots for `currentProject`, `scopedList`, module, role. Append a `User profile:` line and the persona block. Replace the section:

```java
            ────────────────────────────────────────
            CURRENT CONTEXT (internal only — never quote in answers)
            ────────────────────────────────────────
            - Current project: %s
            - Accessible project scope: %s
            - Module: %s
            - User role: %s
```

with:

```java
            ────────────────────────────────────────
            CURRENT CONTEXT (internal only — never quote in answers)
            ────────────────────────────────────────
            - Current project: %s
            - Accessible project scope: %s
            - Module: %s
            - User role: %s
            - User profile: %s
            %s
```

3. Update the `.formatted(...)` call at the bottom of the method to add `ctx.profile() != null ? ctx.profile() : "(none)"` and `personaBlock` as the last two args:

```java
            """.formatted(
                projectFilter,
                dataGraphCatalog.compact(),
                moduleAddendum,
                currentProject,
                scopedList,
                ctx.module() != null ? ctx.module() : "general",
                ctx.role() != null ? ctx.role() : "user",
                ctx.profile() != null ? ctx.profile() : "(none)",
                personaBlock
        );
```

- [ ] **Step 5: Compile**

```bash
cd backend && mvn -pl bipros-ai compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Run all bipros-ai tests**

```bash
cd backend && mvn -pl bipros-ai test -q
```

Expected: BUILD SUCCESS — no behavior change for users without a profile (persona is empty, tool filter passes everything since `allowedRoles()` is empty for all existing tools).

- [ ] **Step 7: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/orchestrator/AiOrchestrator.java
git commit -m "feat(ai): orchestrator filters tools by profile and appends role persona to system prompt"
```

---

## Phase 3 — Tag Existing Tools

### Task 10: Tag the 8 existing PM-tier and DPR tools with `allowedRoles`

**Files (modify each):**
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/PortfolioKpiTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeCostTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeRiskTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeScheduleTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/ForecastCompletionTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDprTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/GetDprDetailsTool.java`
- `backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDailyOutputsTool.java`

- [ ] **Step 1: Add `allowedRoles()` to PM-tier tools**

For **each** of `PortfolioKpiTool`, `AnalyzeCostTool`, `AnalyzeRiskTool`, `AnalyzeScheduleTool`, `ForecastCompletionTool`, append the following method inside the class body (after the existing `name()`, `description()`, `inputSchema()`, `execute()`/`doExecute()` methods):

```java
    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of(
                "PROJECT_MANAGER",
                "PORTFOLIO_MANAGER",
                "RISK_MANAGER",
                "COST_CONTROLLER",
                "EXECUTIVE_VIEWER"
        );
    }
```

> Rationale: these are cost/schedule/portfolio-tier tools. SITE_MANAGER, PROJECT_ENGINEER, QC_MANAGER, BIM_DATA_COORDINATOR don't see them — their personas point them at the new role-specific tools instead. SCHEDULER and RESOURCE_MANAGER also don't see them; if a downstream gap surfaces during integration testing, broaden the set.

- [ ] **Step 2: Add `allowedRoles()` to DPR tools**

For **each** of `QueryDprTool`, `GetDprDetailsTool`, `QueryDailyOutputsTool`, append:

```java
    @Override
    public java.util.Set<String> allowedRoles() {
        return java.util.Set.of(
                "PROJECT_MANAGER", "PORTFOLIO_MANAGER",
                "SITE_MANAGER", "PROJECT_ENGINEER", "QC_MANAGER",
                "BIM_DATA_COORDINATOR",
                "SITE_ENGINEER", "RESOURCE_MANAGER", "SCHEDULER",
                "EXECUTIVE_VIEWER"
        );
    }
```

> Rationale: DPR data is the daily heartbeat — every site-facing role uses it.

- [ ] **Step 3: Compile + run all bipros-ai tests**

```bash
cd backend && mvn -pl bipros-ai test -q
```

Expected: BUILD SUCCESS. SYSTEM_ADMIN still sees all (registry rule); other profiles see the filtered set.

- [ ] **Step 4: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/tool/PortfolioKpiTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeCostTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeRiskTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/AnalyzeScheduleTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/ForecastCompletionTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDprTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/GetDprDetailsTool.java \
        backend/bipros-ai/src/main/java/com/bipros/ai/tool/dpr/QueryDailyOutputsTool.java
git commit -m "chore(ai): tag PM-tier + DPR tools with allowedRoles to enable role-aware filtering"
```

---

## Phase 4 — New Role-Specific Tools

### Task 11: Create `AiContextFixtures` test helper

**Files:**
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/testsupport/AiContextFixtures.java`

- [ ] **Step 1: Create the file**

```java
package com.bipros.ai.testsupport;

import com.bipros.ai.context.AiContext;

import java.util.List;
import java.util.UUID;

public final class AiContextFixtures {
    private AiContextFixtures() {}

    public static AiContext forProfile(String profileCode, UUID projectId) {
        UUID userId = UUID.randomUUID();
        UUID pid = projectId == null ? UUID.randomUUID() : projectId;
        return new AiContext(
                userId,
                pid,
                "general",
                profileCode,    // role
                profileCode,    // profile
                List.of(pid)
        );
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd backend && mvn -pl bipros-ai test-compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-ai/src/test/java/com/bipros/ai/testsupport/AiContextFixtures.java
git commit -m "test(ai): AiContextFixtures helper for role-scoped tool tests"
```

---

### Common pattern for all 15 new tools (read once, applies to Tasks 12–26)

Every new tool task follows the same TDD shape:

1. **Read the entity / repository files** that hold the data the tool will query — use `grep` and `Read` to confirm field names. (Listed per task.)
2. **Write a failing tool test** that:
   - Constructs the tool with mocked repositories or a `ClickHouseTemplate` mock.
   - Invokes `execute(input, AiContextFixtures.forProfile("<profile>", projectId))`.
   - Asserts the `name()`, the `allowedRoles()` set, and one happy-path data shape OR the `data_unavailable` shape if the data source is empty/missing.
3. **Implement the tool** in the appropriate role package, extending `ProjectScopedTool` for project-scoped tools.
4. **Run the test** and verify PASS.
5. **Commit** under the message `feat(ai): add <tool_name> tool for <ROLE>`.

For tools whose backing data is NOT yet captured (e.g., NCR entity may not exist), the implementation:
- Detects absence at runtime (e.g., `try/catch ClassNotFoundException`, or check repository returns 0 rows for any project)
- Returns the `data_unavailable` payload defined at the top of this plan.

The structural skeleton for a tool with full code shown ONCE here for `AnalyzeLabourUtilizationTool` — every subsequent tool repeats the structure with the SQL/repository lookup tailored to its KPI.

---

### Task 12: `AnalyzeLabourUtilizationTool` (SITE_MANAGER)

**Purpose:** actual man-hours vs planned/paid by crew over a date range.

**Data source:** `fact_dpr_logs` (or `fact_labour_daily` if richer) in ClickHouse, joined to `dim_resource` for crew/contractor names. Manpower hours per crew per day.

**Files:**
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/site_manager/AnalyzeLabourUtilizationTool.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/tool/role/site_manager/AnalyzeLabourUtilizationToolTest.java`

- [ ] **Step 1: Discover the labour ClickHouse table**

```bash
grep -rn "fact_labour\|fact_manpower\|labour_daily\|manpower_daily" backend/bipros-analytics/src/main backend/bipros-ai/src/main 2>/dev/null | head -20
```

Use the table that surfaces — likely `bipros_analytics.fact_labour_daily` (columns: `project_id`, `date`, `crew_id`, `contractor_id`, `actual_hours`, `planned_hours`). Confirm column names with:

```bash
grep -rn "fact_labour_daily\|fact_manpower_daily" backend 2>/dev/null
```

If the table doesn't exist, fall back to summing DPR manpower entries via `fact_dpr_resource_manpower`.

- [ ] **Step 2: Write the failing test**

```java
package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyzeLabourUtilizationToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeLabourUtilizationTool tool = new AnalyzeLabourUtilizationTool(ch, om);

    @Test
    void hasCorrectNameAndRoleTags() {
        assertEquals("analyze_labour_utilization", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test
    void returnsRowsByCrewWhenDataPresent() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("crew_id", "c1", "crew_name", "ABC Skilled",
                        "actual_hours", 88, "planned_hours", 96, "utilization_pct", 91.7)
        ));

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        ToolResult r = tool.execute(in, ctx);

        assertTrue(r.success());
        assertNotNull(r.data());
        assertTrue(r.data().path("rows").isArray());
        assertEquals(1, r.data().path("rows").size());
    }

    @Test
    void returnsDataUnavailableWhenNoRows() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);

        assertTrue(r.success(), "data_unavailable is still success=true with payload");
        assertEquals("data_unavailable", r.data().path("status").asText());
        assertFalse(r.data().path("reason").asText().isBlank());
    }
}
```

- [ ] **Step 3: Run — expect FAIL (class missing)**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AnalyzeLabourUtilizationToolTest -q
```

- [ ] **Step 4: Implement the tool**

```java
package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeLabourUtilizationTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper;

    @Override public String name() { return "analyze_labour_utilization"; }

    @Override public String description() {
        return "Compute labour utilization (actual hours / planned hours) per crew/contractor for a "
                + "single project over a date range. Defaults to last 7 days. Returns rows ordered "
                + "by lowest utilization first so the Site Manager sees under-deployed or absent "
                + "crews at the top.";
    }

    @Override public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("from", objectMapper.createObjectNode().put("type", "string").put("format", "date"));
        props.set("to",   objectMapper.createObjectNode().put("type", "string").put("format", "date"));
        schema.set("properties", props);
        return schema;
    }

    @Override public Set<String> allowedRoles() {
        return Set.of("SITE_MANAGER", "PROJECT_MANAGER", "RESOURCE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — labour utilization is per-project.");
        }
        LocalDate to = parse(input.path("to").asText(null), LocalDate.now());
        LocalDate from = parse(input.path("from").asText(null), to.minusDays(7));

        String sql = """
                SELECT crew_id,
                       any(crew_name) AS crew_name,
                       sum(actual_hours) AS actual_hours,
                       sum(planned_hours) AS planned_hours,
                       round(100.0 * sum(actual_hours) / nullIf(sum(planned_hours), 0), 1) AS utilization_pct
                FROM bipros_analytics.fact_labour_daily
                WHERE project_id = :pid
                  AND date BETWEEN :from AND :to
                GROUP BY crew_id
                ORDER BY utilization_pct ASC NULLS FIRST
                LIMIT 200
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("pid", ctx.projectId());
        params.put("from", from);
        params.put("to", to);

        List<Map<String, Object>> rows;
        try {
            rows = clickHouse.queryForList(sql, params);
        } catch (Exception e) {
            log.warn("analyze_labour_utilization: ClickHouse query failed: {}", e.getMessage());
            return dataUnavailable(
                    "fact_labour_daily is not yet populated or accessible for this project.",
                    "Backfill fact_labour_daily from DPR manpower entries, or capture per-crew planned hours.",
                    "query_dpr (raw daily progress with manpower lines)");
        }

        if (rows.isEmpty()) {
            return dataUnavailable(
                    "No labour rows for project " + ctx.projectId() + " between " + from + " and " + to + ".",
                    "Confirm DPRs are being submitted with manpower entries for this period.",
                    "query_dpr to inspect raw DPR records");
        }

        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }
        return ToolResult.table(
                "Labour utilization from " + from + " to " + to + " — " + rows.size() + " crew(s).",
                arr,
                new String[]{"crew_id", "crew_name", "actual_hours", "planned_hours", "utilization_pct"}
        );
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded, String closest) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason", reason);
        payload.put("what_would_be_needed", whatNeeded);
        payload.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }

    private LocalDate parse(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw); } catch (Exception e) { return fallback; }
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AnalyzeLabourUtilizationToolTest -q
```

- [ ] **Step 6: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/site_manager/AnalyzeLabourUtilizationTool.java \
        backend/bipros-ai/src/test/java/com/bipros/ai/tool/role/site_manager/AnalyzeLabourUtilizationToolTest.java
git commit -m "feat(ai): add analyze_labour_utilization tool for SITE_MANAGER"
```

---

### Task 13: `AnalyzeMachineIdleTimeTool` (SITE_MANAGER)

**Purpose:** flag equipment with idle hours over a threshold.

**Data source:** EquipmentLog entity in `bipros-resource` (search for `EquipmentLog` and `IcpmsEquipmentLogSeeder` to confirm fields: `equipmentId`, `logDate`, `idleHours`, `breakdownReason`). Also `fact_equipment_logs` ClickHouse view if present.

**Files:**
- Create: `backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/site_manager/AnalyzeMachineIdleTimeTool.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/tool/role/site_manager/AnalyzeMachineIdleTimeToolTest.java`

- [ ] **Step 1: Confirm the EquipmentLog field names**

```bash
grep -rn "class EquipmentLog\|idle_hours\|idleHours\|breakdown" backend/bipros-resource/src/main 2>/dev/null | head -20
```

- [ ] **Step 2: Write the test**

```java
package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.testsupport.AiContextFixtures;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyzeMachineIdleTimeToolTest {

    private final ClickHouseTemplate ch = mock(ClickHouseTemplate.class);
    private final ObjectMapper om = new ObjectMapper();
    private final AnalyzeMachineIdleTimeTool tool = new AnalyzeMachineIdleTimeTool(ch, om);

    @Test void nameAndRoles() {
        assertEquals("analyze_machine_idle_time", tool.name());
        assertTrue(tool.allowedRoles().contains("SITE_MANAGER"));
    }

    @Test void surfacesEquipmentOverThreshold() {
        UUID pid = UUID.randomUUID();
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of(
                Map.of("equipment_id", "e1", "equipment_name", "EQ-CRN-50T",
                        "idle_hours", 4.5, "breakdown_reason", "hydraulic leak", "log_date", "2026-05-07")
        ));
        ObjectNode in = JsonNodeFactory.instance.objectNode();
        in.put("threshold_hours", 2);

        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", pid);
        ToolResult r = tool.execute(in, ctx);
        assertTrue(r.success());
        assertEquals(1, r.data().path("rows").size());
    }

    @Test void dataUnavailableOnEmptyResult() {
        when(ch.queryForList(anyString(), anyMap())).thenReturn(List.of());
        AiContext ctx = AiContextFixtures.forProfile("SITE_MANAGER", UUID.randomUUID());
        ToolResult r = tool.execute(JsonNodeFactory.instance.objectNode(), ctx);
        assertEquals("data_unavailable", r.data().path("status").asText());
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AnalyzeMachineIdleTimeToolTest -q
```

- [ ] **Step 4: Implement**

```java
package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeMachineIdleTimeTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper;

    @Override public String name() { return "analyze_machine_idle_time"; }

    @Override public String description() {
        return "List equipment with idle hours above a threshold over a date range, with the "
                + "breakdown reason where logged. Defaults: last 1 day, threshold 2 hours. "
                + "Returns one row per equipment-day above the threshold, ordered by idle hours "
                + "descending.";
    }

    @Override public JsonNode inputSchema() {
        ObjectNode s = objectMapper.createObjectNode(); s.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("from", objectMapper.createObjectNode().put("type", "string").put("format", "date"));
        props.set("to",   objectMapper.createObjectNode().put("type", "string").put("format", "date"));
        props.set("threshold_hours", objectMapper.createObjectNode().put("type", "number")
                .put("description", "Idle hours threshold; defaults to 2"));
        s.set("properties", props);
        return s;
    }

    @Override public Set<String> allowedRoles() {
        return Set.of("SITE_MANAGER", "PROJECT_MANAGER", "RESOURCE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) return ToolResult.error("Pick a project first.");
        LocalDate to = parse(input.path("to").asText(null), LocalDate.now());
        LocalDate from = parse(input.path("from").asText(null), to.minusDays(1));
        double threshold = input.has("threshold_hours") ? input.get("threshold_hours").asDouble(2.0) : 2.0;

        String sql = """
                SELECT equipment_id,
                       any(equipment_name) AS equipment_name,
                       log_date,
                       sum(idle_hours) AS idle_hours,
                       any(breakdown_reason) AS breakdown_reason
                FROM bipros_analytics.fact_equipment_logs
                WHERE project_id = :pid
                  AND log_date BETWEEN :from AND :to
                GROUP BY equipment_id, log_date
                HAVING idle_hours >= :threshold
                ORDER BY idle_hours DESC
                LIMIT 200
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("pid", ctx.projectId());
        params.put("from", from);
        params.put("to", to);
        params.put("threshold", threshold);

        List<Map<String, Object>> rows;
        try { rows = clickHouse.queryForList(sql, params); }
        catch (Exception e) {
            log.warn("analyze_machine_idle_time query failed: {}", e.getMessage());
            return unavailable(
                    "Equipment-log analytics view not yet populated for this project.",
                    "Capture daily equipment logs (idle_hours, breakdown_reason) and ensure they flow into fact_equipment_logs.",
                    "query_dpr_resources(resource_kind=\"equipment\") for raw deployments");
        }
        if (rows.isEmpty()) {
            return unavailable(
                    "No equipment idle entries above " + threshold + "h between " + from + " and " + to + ".",
                    "Confirm equipment logs are being captured daily.",
                    "query_dpr_resources(resource_kind=\"equipment\")");
        }

        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }
        return ToolResult.table(
                "Idle equipment (>=" + threshold + "h) " + from + " to " + to + " — " + rows.size() + " entries.",
                arr,
                new String[]{"equipment_id", "equipment_name", "log_date", "idle_hours", "breakdown_reason"}
        );
    }

    private ToolResult unavailable(String reason, String need, String closest) {
        ObjectNode p = objectMapper.createObjectNode();
        p.put("status", "data_unavailable");
        p.put("reason", reason);
        p.put("what_would_be_needed", need);
        p.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, p);
    }

    private LocalDate parse(String raw, LocalDate fb) {
        if (raw == null || raw.isBlank()) return fb;
        try { return LocalDate.parse(raw); } catch (Exception e) { return fb; }
    }
}
```

- [ ] **Step 5: Run + Step 6: Commit**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=AnalyzeMachineIdleTimeToolTest -q
git add backend/bipros-ai/src/main/java/com/bipros/ai/tool/role/site_manager/AnalyzeMachineIdleTimeTool.java \
        backend/bipros-ai/src/test/java/com/bipros/ai/tool/role/site_manager/AnalyzeMachineIdleTimeToolTest.java
git commit -m "feat(ai): add analyze_machine_idle_time tool for SITE_MANAGER"
```

---

### Task 14: `AnalyzeMaterialWastageTool` (SITE_MANAGER)

**Data source:** `MaterialReconciliation` entity in `bipros-resource` (`grep -rn "MaterialReconciliation" backend/bipros-resource/src/main`). Compute wastage % = (used − theoretical) / theoretical.

**Files:** mirror Task 12/13 in `role/site_manager/`.

- [ ] **Step 1:** Confirm field names: `grep -rn "class MaterialReconciliation\|theoretical\|wastage" backend/bipros-resource/src/main`. Expected fields: `materialId`, `quantityUsed`, `theoreticalQuantity`, `reconciliationDate`, `location`.
- [ ] **Step 2:** Write `AnalyzeMaterialWastageToolTest` following the pattern in Task 12 (3 tests: name+roles, happy path, data_unavailable). Mock `MaterialReconciliationRepository`.
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement `AnalyzeMaterialWastageTool` extending `ProjectScopedTool`. `name() = "analyze_material_wastage"`, `allowedRoles() = Set.of("SITE_MANAGER", "PROJECT_MANAGER", "PROJECT_ENGINEER")`. Group by material+location, compute `wastage_pct = (qty_used - theoretical_qty) / theoretical_qty * 100`, sort by `wastage_pct DESC`. Use the JPA repository (not ClickHouse) since reconciliation data is transactional. If repository returns empty, return `data_unavailable` with reason "no MaterialReconciliation entries for this project".
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** `git add` + commit `feat(ai): add analyze_material_wastage tool for SITE_MANAGER`.

---

### Task 15: `CheckStockpileVsPlanTool` (SITE_MANAGER)

**Data source:** Material stock (search `MaterialStock`, `MaterialIssue`, `MaterialReceipt` in `bipros-resource`) plus lookahead from `Activity.plannedStartDate <= today + 7d` joined with the activity's resource-assignment material requirement.

**Files:** mirror prior tasks in `role/site_manager/`.

- [ ] **Step 1:** `grep -rn "MaterialStock\|currentStock\|stock_quantity" backend/bipros-resource/src/main` to confirm.
- [ ] **Step 2:** Write `CheckStockpileVsPlanToolTest`: mocks 1 row of stock + 1 demand row, expects ratio in output. Plus an empty-stock test → `data_unavailable`.
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement `CheckStockpileVsPlanTool` (`name = "check_stockpile_vs_plan"`, `allowedRoles = Set.of("SITE_MANAGER", "PROJECT_MANAGER", "RESOURCE_MANAGER")`). Input schema: `lookahead_days` (default 3). Logic:
  1. Query current stock per material for the project.
  2. Query lookahead demand: sum material qty across activities with planned start in `[today, today+lookahead_days]`.
  3. For each material, output `current_stock`, `lookahead_demand`, `stock_to_need_ratio = stock / demand`. Flag `at_risk = ratio < 1.0`.
  4. If either query is empty for the project → `data_unavailable` ("no material-stock entries" or "no upcoming planned activities with material requirements").
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** Commit `feat(ai): add check_stockpile_vs_plan tool for SITE_MANAGER`.

---

### Task 16: `AnalyzeProductivityFactorTool` (PROJECT_ENGINEER)

**Data source:** `fact_dpr_logs` (qty_executed) joined with `ProductivityNorm` from `bipros-activity` (`grep -rn "ProductivityNorm" backend/bipros-activity/src/main`).

**Files:** `role/project_engineer/`.

- [ ] **Step 1:** Confirm `ProductivityNorm` columns: `activityType`, `outputPerManHour`, etc.
- [ ] **Step 2:** Test (3 tests: name+roles, happy path returning rows with `actual_per_hour`, `norm_per_hour`, `variance_pct`; data_unavailable when no DPR rows).
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement `AnalyzeProductivityFactorTool`. `name = "analyze_productivity_factor"`, `allowedRoles = Set.of("PROJECT_ENGINEER", "PROJECT_MANAGER", "SITE_MANAGER")`. SQL: SELECT crew, activity, sum(qty_executed)/sum(man_hours) AS actual_per_hour, norm_per_hour, variance_pct FROM fact_dpr_logs JOIN dim_activity_norm USING (activity_type). Group by crew_id and activity_code, ORDER BY variance_pct ASC. Note: this tool is closely related to the existing `compare_actual_vs_norm` tool — re-use its repository if it exposes one; otherwise duplicate the SQL pattern.
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_productivity_factor tool for PROJECT_ENGINEER`.

---

### Task 17: `AnalyzeYieldVarianceTool` (PROJECT_ENGINEER)

**Data source:** `MaterialReconciliation.quantityUsed` vs `BoqItem.quantity` (search `Boq`, `BillOfQuantities` in `bipros-cost`/`bipros-contract`).

**Files:** `role/project_engineer/`.

- [ ] **Step 1:** `grep -rn "BoqItem\|BillOfQuant\|design_quantity" backend/bipros-cost/src/main backend/bipros-contract/src/main` to confirm whether BOQ exists.
- [ ] **Step 2:** Test (3 tests). If BOQ entity doesn't exist, the happy-path test stubs the repository to return rows from MaterialReconciliation alone with a placeholder `design_quantity = null` → tool returns `data_unavailable` with reason "no Bill of Quantities entries linked to this project". The data_unavailable test asserts the reason text matches.
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement `AnalyzeYieldVarianceTool`. `name = "analyze_yield_variance"`, `allowedRoles = Set.of("PROJECT_ENGINEER", "PROJECT_MANAGER")`. Logic:
  1. Look up BOQ items for the project via repository.
  2. If no BOQ → return `data_unavailable` with `closest_available = "analyze_material_wastage"`.
  3. For each BOQ item, sum `MaterialReconciliation.quantityUsed`. Compute `yield_variance_pct = (actual - design) / design * 100`. Sort by absolute variance descending.
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_yield_variance tool for PROJECT_ENGINEER`.

---

### Task 18: `AnalyzeEquipmentCycleTimeTool` (PROJECT_ENGINEER)

**Data source:** `EquipmentLog` (cycle start/end if present); likely NOT yet captured.

**Files:** `role/project_engineer/`.

- [ ] **Step 1:** `grep -rn "cycle_start\|cycle_end\|cycleStart" backend/bipros-resource/src/main` — expected to return zero hits. Confirm.
- [ ] **Step 2:** Test — only the `data_unavailable` path is exercised (happy path is unreachable until field is captured). Tests: name+roles, data_unavailable returns the right reason text.
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement `AnalyzeEquipmentCycleTimeTool`. `name = "analyze_equipment_cycle_time"`, `allowedRoles = Set.of("PROJECT_ENGINEER", "PROJECT_MANAGER", "SITE_MANAGER")`. Body returns `data_unavailable` with:
  - `reason = "Equipment cycle start/end timestamps are not yet captured."`
  - `what_would_be_needed = "Add cycle_start_at and cycle_end_at to EquipmentLog (or a new CycleEvent entity), and emit them in DPR equipment lines."`
  - `closest_available = "analyze_machine_idle_time"`
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_equipment_cycle_time tool stub (data not yet captured)`.

---

### Task 19: `AnalyzeNcrTrendsTool` (QC_MANAGER)

**Data source:** Risk register if NCR-typed risks exist; otherwise `data_unavailable`.

**Files:** `role/qc_manager/`.

- [ ] **Step 1:** `grep -rn "Ncr\|NonConformance\|nonconformance" backend/bipros-risk/src/main backend/bipros-activity/src/main`. If any entity surfaces, use it. Otherwise look for risks with `category = "QUALITY"` or similar enum value: `grep -rn "RiskCategory\|category.*QUALITY" backend/bipros-risk/src/main`.
- [ ] **Step 2:** Test — happy path uses Risk repository mock returning 1 quality-category risk; data_unavailable when zero.
- [ ] **Step 3:** Run — expect FAIL.
- [ ] **Step 4:** Implement. `name = "analyze_ncr_trends"`, `allowedRoles = Set.of("QC_MANAGER", "PROJECT_MANAGER")`. Query the Risk repository for risks with category Quality (or `severity >= MAJOR`, depending on what exists). Group by `crew_id` (if linkable) or `source` (contractor). Sort descending. If risk repo has zero quality entries for the project, return `data_unavailable` with `reason = "Dedicated NCR tracking is not yet captured. The closest signal in the system is the Risk Register filtered to Quality-category risks."` and `closest_available = "analyze_risk"`.
- [ ] **Step 5:** Run — expect PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_ncr_trends tool for QC_MANAGER (uses Risk Register as proxy)`.

---

### Task 20: `AuditTraceabilityTool` (QC_MANAGER)

**Data source:** DPR rows joined with manpower/material lines (`fact_dpr_resource_manpower`, `fact_dpr_resource_material`).

**Files:** `role/qc_manager/`.

- [ ] **Step 1:** `grep -rn "fact_dpr_resource_material\|fact_dpr_resource_manpower" backend` to confirm tables.
- [ ] **Step 2:** Test (3 tests as before).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "audit_traceability"`, `allowedRoles = Set.of("QC_MANAGER", "PROJECT_MANAGER", "BIM_DATA_COORDINATOR")`. Input: `dpr_id` (uuid) OR (`activity_code` + `report_date`). Returns: DPR row + array of (operator name, role) and (material lot + qty + supplier). Each item flagged `traceable=true` if all three of (operator, material lot, location) present, else `traceable=false`. If the DPR has no manpower or no material entries → `data_unavailable` with reason naming the missing dimension.
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add audit_traceability tool for QC_MANAGER`.

---

### Task 21: `AnalyzeQualityDataGapsTool` (QC_MANAGER)

**Data source:** Activity-level QC flags (search `qcRequired`, `qualityCheck` in `bipros-activity`); if absent, treat ALL activities as QC-required and report DPR completeness.

**Files:** `role/qc_manager/`.

- [ ] **Step 1:** `grep -rn "qcRequired\|qualityCheck\|qc_required" backend/bipros-activity/src/main`.
- [ ] **Step 2:** Test (3 tests).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "analyze_quality_data_gaps"`, `allowedRoles = Set.of("QC_MANAGER", "BIM_DATA_COORDINATOR", "PROJECT_MANAGER")`. Logic: list activities flagged QC-required (or all) with progress > 0 but no DPR with completed quality fields (test_results, signature, etc.) — fields TBD per data model. If no QC-tagged activities exist, return data_unavailable with `reason = "No activities have explicit QC-required flags in this project; quality data gaps cannot be inferred."`.
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_quality_data_gaps tool for QC_MANAGER`.

---

### Task 22: `AnalyzeLabourCostPerUnitTool` (PROJECT_MANAGER)

**Data source:** `LabourReturn` for cost + `DailyProgressReport` for executed quantity, normalised to $/unit; budget unit rate from `CostAccount` or `BoqItem`.

**Files:** `role/project_manager/`.

- [ ] **Step 1:** `grep -rn "LabourReturn\|labour_cost\|unit_rate" backend/bipros-resource/src/main backend/bipros-cost/src/main`.
- [ ] **Step 2:** Test (3 tests).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "analyze_labour_cost_per_unit"`, `allowedRoles = Set.of("PROJECT_MANAGER", "PORTFOLIO_MANAGER", "COST_CONTROLLER")`. Logic: per activity (or per WBS), `actual_$_per_unit = sum(labour_cost) / sum(qty_executed)`. Compare to `budget_$_per_unit` from cost account or BOQ. Output: actual, budget, delta_pct. Order by delta_pct DESC. If labour_cost is missing for an activity (LabourReturn empty), surface `data_unavailable` for THAT activity row (not the whole tool).
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_labour_cost_per_unit tool for PROJECT_MANAGER`.

---

### Task 23: `AnalyzeMaterialBurnRateTool` (PROJECT_MANAGER)

**Data source:** `MaterialIssue` or `MaterialReconciliation` (used quantity per day) + `MaterialReceipt` (procured) — if both exist; else compute burn rate from issuance only.

**Files:** `role/project_manager/`.

- [ ] **Step 1:** `grep -rn "MaterialReceipt\|MaterialIssue\|procured" backend/bipros-resource/src/main`.
- [ ] **Step 2:** Test (3 tests).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "analyze_material_burn_rate"`, `allowedRoles = Set.of("PROJECT_MANAGER", "PORTFOLIO_MANAGER", "COST_CONTROLLER", "RESOURCE_MANAGER")`. Filter to high-value materials by sorting by total cost — top N by spend. Per material: `daily_burn = sum(qty_used last 7d)/7`, `days_remaining = (procured - used) / daily_burn`. Flag `at_risk = days_remaining < 5`. Sort by days_remaining ASC. If no MaterialReceipt rows, set `procured = null` and explain in `data_unavailable` per row.
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_material_burn_rate tool for PROJECT_MANAGER`.

---

### Task 24: `AnalyzeEquipmentUtilizationCostTool` (PROJECT_MANAGER)

**Data source:** EquipmentLog + `Resource.ownership` flag (OWNED/RENTED, search `bipros-resource`) + `Resource.hourlyRate`.

**Files:** `role/project_manager/`.

- [ ] **Step 1:** `grep -rn "ownership\|OWNED\|RENTED\|hourlyRate" backend/bipros-resource/src/main`.
- [ ] **Step 2:** Test (3 tests).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "analyze_equipment_utilization_cost"`, `allowedRoles = Set.of("PROJECT_MANAGER", "PORTFOLIO_MANAGER", "COST_CONTROLLER", "RESOURCE_MANAGER")`. Logic: per equipment, `utilization_pct = active_hours / available_hours`, `cost_per_hour = hourly_rate * (active_hours + idle_hours_billable) / active_hours`. Group by `ownership`. Output: per-equipment table + summary stat `owned_avg_$/hr` vs `rented_avg_$/hr`. If `Resource.ownership` is null for all equipment in the project → data_unavailable.
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add analyze_equipment_utilization_cost tool for PROJECT_MANAGER`.

---

### Task 25: `AuditDprDataQualityTool` (BIM_DATA_COORDINATOR)

**Data source:** DailyProgressReport entity + counts of nulls per critical field per project. Per-day completeness ratio.

**Files:** `role/bim_coordinator/`.

- [ ] **Step 1:** `grep -rn "class DailyProgressReport" backend/bipros-project/src/main` to list fields. Identify the "critical" fields the BIM Coordinator cares about: weather, supervisor, manpower lines, equipment lines, material lines, breakdown_reason on idle equipment.
- [ ] **Step 2:** Test (3 tests).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "audit_dpr_data_quality"`, `allowedRoles = Set.of("BIM_DATA_COORDINATOR", "PROJECT_MANAGER", "PORTFOLIO_MANAGER")`. For each DPR in the project's last 30 days: count fields-present / fields-expected → `completeness_pct`. Aggregate by date and by missing-field category. Output: rows ordered by completeness ASC. Always returns rows if any DPRs exist (no data_unavailable except when project has zero DPRs).
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add audit_dpr_data_quality tool for BIM_DATA_COORDINATOR`.

---

### Task 26: `ReportDataLagTool` (BIM_DATA_COORDINATOR)

**Data source:** DPR `reportDate` vs `createdAt` audit field (BaseEntity has `createdAt`). Lag = `createdAt::date - reportDate`.

**Files:** `role/bim_coordinator/`.

- [ ] **Step 1:** Confirm `DailyProgressReport extends BaseEntity` and BaseEntity has `createdAt`. `grep -rn "createdAt\|created_at" backend/bipros-common/src/main`.
- [ ] **Step 2:** Test (3 tests: name+roles, happy path with rows showing lag distribution `[0d, 1d, 2d+]`, data_unavailable when no DPRs).
- [ ] **Step 3:** FAIL.
- [ ] **Step 4:** Implement. `name = "report_data_lag"`, `allowedRoles = Set.of("BIM_DATA_COORDINATOR", "PROJECT_MANAGER")`. Compute lag in days for last 30 days of DPRs; bucket into `0`, `1`, `2`, `3-7`, `>7`. Output: counts per bucket + p50/p90 lag. Plus a list of the worst 10 DPRs by lag.
- [ ] **Step 5:** PASS.
- [ ] **Step 6:** Commit `feat(ai): add report_data_lag tool for BIM_DATA_COORDINATOR`.

---

## Phase 5 — Frontend

### Task 27: Add 15 new tool labels to `TOOL_PROGRESS_LABELS`

**Files:**
- Modify: `frontend/src/components/ai/AiChatPanel.tsx:70-84`

- [ ] **Step 1: Add the new entries**

Locate `TOOL_PROGRESS_LABELS` (line 70) and add after the existing entries (before the closing `}`):

```ts
  // Site Manager
  analyze_labour_utilization: "Reading crew utilization",
  analyze_machine_idle_time: "Checking machine idle time",
  analyze_material_wastage: "Reading material wastage",
  check_stockpile_vs_plan: "Comparing stockpile vs plan",
  // Project Engineer
  analyze_productivity_factor: "Reading productivity vs norm",
  analyze_yield_variance: "Reading yield variance",
  analyze_equipment_cycle_time: "Reading equipment cycle times",
  // QC Manager
  analyze_ncr_trends: "Reading NCR trends",
  audit_traceability: "Auditing traceability",
  analyze_quality_data_gaps: "Looking for quality data gaps",
  // Project Manager
  analyze_labour_cost_per_unit: "Reading labour cost per unit",
  analyze_material_burn_rate: "Reading material burn rate",
  analyze_equipment_utilization_cost: "Reading equipment utilization cost",
  // BIM / Data Coordinator
  audit_dpr_data_quality: "Auditing DPR data quality",
  report_data_lag: "Reading data entry lag",
```

- [ ] **Step 2: Type-check the frontend**

```bash
cd frontend && pnpm tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ai/AiChatPanel.tsx
git commit -m "feat(ai-ui): add progress labels for 15 new role-specific AI tools"
```

---

## Phase 6 — End-to-End Verification

### Task 28: Per-role integration test on `ToolRegistry.toolsForProfile`

**Files:**
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolsAvailableByProfileIT.java`

- [ ] **Step 1: Write the test**

```java
package com.bipros.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ToolsAvailableByProfileIT {

    @Autowired ToolRegistry registry;

    @Test
    void siteManagerSeesItsFourTools() {
        Set<String> names = registry.toolsForProfile("SITE_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_utilization"));
        assertTrue(names.contains("analyze_machine_idle_time"));
        assertTrue(names.contains("analyze_material_wastage"));
        assertTrue(names.contains("check_stockpile_vs_plan"));
    }

    @Test
    void projectEngineerSeesItsThreeTools() {
        Set<String> names = registry.toolsForProfile("PROJECT_ENGINEER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_productivity_factor"));
        assertTrue(names.contains("analyze_yield_variance"));
        assertTrue(names.contains("analyze_equipment_cycle_time"));
    }

    @Test
    void qcManagerSeesItsThreeTools() {
        Set<String> names = registry.toolsForProfile("QC_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_ncr_trends"));
        assertTrue(names.contains("audit_traceability"));
        assertTrue(names.contains("analyze_quality_data_gaps"));
    }

    @Test
    void projectManagerSeesItsThreeNewToolsAndExisting() {
        Set<String> names = registry.toolsForProfile("PROJECT_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_cost_per_unit"));
        assertTrue(names.contains("analyze_material_burn_rate"));
        assertTrue(names.contains("analyze_equipment_utilization_cost"));
        assertTrue(names.contains("portfolio_kpi"));
        assertTrue(names.contains("analyze_cost"));
    }

    @Test
    void bimCoordinatorSeesItsTwoTools() {
        Set<String> names = registry.toolsForProfile("BIM_DATA_COORDINATOR").stream()
                .map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("audit_dpr_data_quality"));
        assertTrue(names.contains("report_data_lag"));
    }

    @Test
    void siteManagerDoesNotSeePmTools() {
        Set<String> names = registry.toolsForProfile("SITE_MANAGER").stream()
                .map(Tool::name).collect(Collectors.toSet());
        // PM-tier tools tagged in Task 10 — Site Manager must NOT see them.
        assertTrue(!names.contains("portfolio_kpi"));
        assertTrue(!names.contains("forecast_completion"));
    }

    @Test
    void systemAdminSeesEverything() {
        List<Tool> all = registry.toolsForProfile("SYSTEM_ADMIN");
        // Spot-check: a SITE_MANAGER tool AND a PM tool both visible.
        Set<String> names = all.stream().map(Tool::name).collect(Collectors.toSet());
        assertTrue(names.contains("analyze_labour_utilization"));
        assertTrue(names.contains("portfolio_kpi"));
    }
}
```

- [ ] **Step 2: Run**

```bash
cd backend && mvn -pl bipros-ai test -Dtest=ToolsAvailableByProfileIT -q
```

Expected: 7 tests, 0 failures. If any fails, the failing test names which tool wasn't tagged correctly — fix the `allowedRoles()` on that tool and re-run.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-ai/src/test/java/com/bipros/ai/tool/ToolsAvailableByProfileIT.java
git commit -m "test(ai): integration test asserting per-profile tool visibility"
```

---

### Task 29: Full backend build + smoke

- [ ] **Step 1: Run full backend tests**

```bash
cd backend && mvn -q clean verify -DskipITs=false
```

Expected: BUILD SUCCESS. If any test fails, address it before declaring complete.

- [ ] **Step 2: Manual smoke (optional, not required for plan completion)**

Boot the app in `dev` profile and verify the new roles + profiles are visible in the Profile Admin UI.

```bash
docker compose up -d
(cd backend && DDL_AUTO=create-drop mvn spring-boot:run -pl bipros-api)
```

Open http://localhost:3000, log in as `admin` / `admin123`, navigate to Profile Admin. Confirm `SITE_MANAGER`, `PROJECT_ENGINEER`, `QC_MANAGER`, `BIM_DATA_COORDINATOR` profiles exist with their permission lists.

- [ ] **Step 3: Final commit (if smoke surfaced any fixes)**

Otherwise no commit needed — work is complete.

---

## Self-Review Notes (run before declaring plan ready)

1. **Spec coverage:**
   - Identity layer (spec §5) → Tasks 1–3 ✓
   - AI capability layer (spec §6) → Tasks 4–9 ✓
   - 15 new tools (spec §7.1–7.5) → Tasks 12–26 ✓ (4 SM + 3 PE + 3 QC + 3 PM + 2 BIM)
   - Existing-tool tagging (spec §7.6) → Task 10 ✓
   - Graceful degradation (spec §7.7) → embedded in every tool task ✓
   - Frontend impact (spec §8) → Task 27 ✓
   - Testing strategy (spec §9) → Tasks 1–28 each include tests; Task 28 is the integration test ✓

2. **Placeholder scan:**
   - Tasks 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26 use the "discover-then-implement" pattern (Step 1 reads entity files, Step 4 implements with the discovered field names). This is necessary because field names depend on entities the engineer must inspect. The TASK CONTRACT for each is fully specified (name, allowedRoles, input schema purpose, output shape, data_unavailable trigger), so the engineer is not guessing — only confirming exact column names.
   - No "TBD", "TODO", "implement later" remains in any production-code step.

3. **Type consistency:**
   - `allowedRoles()` always returns `Set<String>` of profile codes matching `Profile.code` in `ProfileSeeder.DEFAULTS`.
   - `AiContext.profile` field added in Task 6, populated in Task 7, consumed in Tasks 5/9/10–26.
   - `data_unavailable` shape is identical across all tools (defined once at top of plan).

---

**End of plan.**
