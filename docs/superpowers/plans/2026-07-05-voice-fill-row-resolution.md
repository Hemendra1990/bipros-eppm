# Voice-Fill Row Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make voice-filled manpower/equipment/material rows survive DPR save by loading rate-book reference data, extending the LLM schema with role FK fields, validating returned IDs, and fixing temperature for reasoning models.

**Architecture:** The backend `DprVoiceFillService` currently loads only supervisors/activities/BOQ as reference data. We extend it to also load manpower/equipment/material rate-book entries (planned + global), add `roleId`/`manpowerRoleRateId`/`equipmentRoleVariantId`/`materialRoleVariantId` to the structured-output schema, validate returned UUIDs, and send `null` temperature for reasoning models. The frontend tightens patch row types and adds user feedback toasts.

**Tech Stack:** Java 23 / Spring Boot 3.5 / JUnit 5 + Mockito (backend), TypeScript / Next.js 16 / React 19 (frontend)

**Spec:** `docs/superpowers/specs/2026-07-05-voice-fill-row-resolution-design.md`

---

## File Structure

**Backend (modify):**
- `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillSchema.java` — add role FK fields to row schemas
- `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java` — inject services, load role reference data, extend prompt, validate IDs, fix temperature, add success log

**Backend (create):**
- `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillSchemaTest.java` — schema field tests
- `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java` — service logic tests

**Frontend (modify):**
- `frontend/src/lib/api/dprApi.ts` — tighten `DprVoicePatch` row types
- `frontend/src/components/dpr/DprActivityForm.tsx` — remove unsafe casts in `applyVoicePatch`
- `frontend/src/components/dpr/DprVoiceAssistant.tsx` — add toast feedback

---

### Task 1: Extend DprVoiceFillSchema with Role FK Fields

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillSchema.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillSchemaTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillSchemaTest.java`:

```java
package com.bipros.ai.voice.dpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DprVoiceFillSchemaTest {

    private final DprVoiceFillSchema schemaBuilder = new DprVoiceFillSchema(new ObjectMapper());

    @Test
    void manpowerRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode manpowerItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("manpower")
            .path("items");

        JsonNode props = manpowerItem.path("properties");
        assertNotNull(props.path("roleId"), "manpower row must include roleId");
        assertNotNull(props.path("manpowerRoleRateId"), "manpower row must include manpowerRoleRateId");
        assertNotNull(props.path("shift"), "manpower row must include shift");

        JsonNode required = manpowerItem.path("required");
        assertTrue(required.isArray(), "manpower row must have a required array");
        var requiredList = new java.util.ArrayList<String>();
        required.forEach(n -> requiredList.add(n.asText()));
        assertTrue(requiredList.contains("roleId"), "roleId must be required");
        assertTrue(requiredList.contains("manpowerRoleRateId"), "manpowerRoleRateId must be required");
        assertTrue(requiredList.contains("shift"), "shift must be required");
    }

    @Test
    void equipmentRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode equipmentItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("equipment")
            .path("items");

        JsonNode props = equipmentItem.path("properties");
        assertNotNull(props.path("roleId"), "equipment row must include roleId");
        assertNotNull(props.path("equipmentRoleVariantId"), "equipment row must include equipmentRoleVariantId");
        assertNotNull(props.path("shift"), "equipment row must include shift");

        JsonNode required = equipmentItem.path("required");
        var requiredList = new java.util.ArrayList<String>();
        required.forEach(n -> requiredList.add(n.asText()));
        assertTrue(requiredList.contains("roleId"));
        assertTrue(requiredList.contains("equipmentRoleVariantId"));
        assertTrue(requiredList.contains("shift"));
    }

    @Test
    void materialRowSchemaIncludesRoleFkFields() {
        JsonNode schema = schemaBuilder.buildSchema();
        JsonNode materialItem = schema
            .path("json_schema").path("schema")
            .path("properties").path("patch")
            .path("properties").path("materials")
            .path("items");

        JsonNode props = materialItem.path("properties");
        assertNotNull(props.path("roleId"), "material row must include roleId");
        assertNotNull(props.path("materialRoleVariantId"), "material row must include materialRoleVariantId");

        JsonNode required = materialItem.path("required");
        var requiredList = new java.util.ArrayList<String>();
        required.forEach(n -> requiredList.add(n.asText()));
        assertTrue(requiredList.contains("roleId"));
        assertTrue(requiredList.contains("materialRoleVariantId"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillSchemaTest -q`
Expected: FAIL — `roleId` / `manpowerRoleRateId` / `equipmentRoleVariantId` / `materialRoleVariantId` / `shift` properties not found in row schemas.

- [ ] **Step 3: Add role FK fields to manpower schema**

In `DprVoiceFillSchema.java`, modify `manpowerSchema()` — add `roleId`, `manpowerRoleRateId`, and `shift` to the properties and `requireAll`:

Replace the existing `manpowerSchema()` method body's properties section and requireAll with:

```java
    props.set("resourceAssignmentId", nullableString("UUID of the resource assignment, or null."));
    props.set("roleId", nullableString(
        "UUID of the manpower role, from the provided manpower roles reference list."));
    props.set("manpowerRoleRateId", nullableString(
        "UUID of the manpower rate variant (variantId from the reference list). "
            + "Must match a listed variantId. Set to null when no match is found."));
    props.set("trade", stringField("Trade name (e.g. Mason, Helper, Electrician). "
        + "Set to the roleName from the matched reference list entry."));
    props.set("category", nullableEnum(
        List.of("SKILLED", "SEMI_SKILLED", "UNSKILLED"),
        "Worker category. Default UNSKILLED if not stated."));
    props.set("shift", nullableEnum(
        List.of("DAY", "NIGHT"),
        "Per-row shift. Default DAY when not stated."));
    props.set("nos", nullableInteger("Number of workers."));
    props.set("workingHours", nullableNumber("Hours worked (regular)."));
    props.set("otHours", nullableNumber("Overtime hours."));
    props.set("contractorName", nullableString("Crew contractor name."));
    props.set("remarks", nullableString("Per-row remarks."));
    requireAll(item, List.of(
        "resourceAssignmentId", "roleId", "manpowerRoleRateId", "trade", "category", "shift",
        "nos", "workingHours", "otHours", "contractorName", "remarks"));
```

- [ ] **Step 4: Add role FK fields to equipment schema**

In `DprVoiceFillSchema.java`, modify `equipmentSchema()` — add `roleId`, `equipmentRoleVariantId`, and `shift`:

```java
    props.set("resourceAssignmentId", nullableString("UUID of the resource assignment, or null."));
    props.set("roleId", nullableString(
        "UUID of the equipment role, from the provided equipment roles reference list."));
    props.set("equipmentRoleVariantId", nullableString(
        "UUID of the equipment variant (variantId from the reference list). "
            + "Must match a listed variantId. Set to null when no match is found."));
    props.set("equipmentType", stringField("Equipment type (e.g. JCB, Excavator, Roller). "
        + "Set to the roleName from the matched reference list entry."));
    props.set("fleetNo", nullableString("Fleet / asset number."));
    props.set("ownership", nullableEnum(
        List.of("OWNED", "HIRED", "SUBCONTRACTOR"),
        "Ownership."));
    props.set("shift", nullableEnum(
        List.of("DAY", "NIGHT"),
        "Per-row shift. Default DAY when not stated."));
    props.set("nos", nullableInteger("Number of units."));
    props.set("workingHours", nullableNumber("Hours run."));
    props.set("idleHours", nullableNumber("Idle hours."));
    props.set("breakdownHours", nullableNumber("Breakdown hours."));
    props.set("fuelLitres", nullableNumber("Fuel in litres."));
    props.set("availabilityStatus", nullableEnum(
        List.of("AVAILABLE", "UTILIZED", "IDLE", "BREAKDOWN"),
        "End-of-day status."));
    props.set("remarks", nullableString("Per-row remarks."));
    requireAll(item, List.of(
        "resourceAssignmentId", "roleId", "equipmentRoleVariantId", "equipmentType", "fleetNo",
        "ownership", "shift", "nos", "workingHours", "idleHours", "breakdownHours",
        "fuelLitres", "availabilityStatus", "remarks"));
```

- [ ] **Step 5: Add role FK fields to materials schema**

In `DprVoiceFillSchema.java`, modify `materialsSchema()` — add `roleId` and `materialRoleVariantId`:

```java
    props.set("resourceAssignmentId", nullableString("UUID of the resource assignment, or null."));
    props.set("roleId", nullableString(
        "UUID of the material role, from the provided material roles reference list."));
    props.set("materialRoleVariantId", nullableString(
        "UUID of the material variant (variantId from the reference list). "
            + "Must match a listed variantId. Set to null when no match is found."));
    props.set("materialName", stringField("Material name (e.g. Cement, Steel TMT, Aggregate 20mm). "
        + "Set to the roleName from the matched reference list entry."));
    props.set("quantity", nullableNumber("Quantity consumed."));
    props.set("unit", nullableString("Unit (e.g. MT, Cum, Bags)."));
    props.set("source", nullableString("Quarry / yard / vendor source."));
    props.set("batchNo", nullableString("Batch / lot number."));
    props.set("vendorName", nullableString("Vendor name."));
    props.set("remarks", nullableString("Per-row remarks."));
    requireAll(item, List.of(
        "resourceAssignmentId", "roleId", "materialRoleVariantId", "materialName", "quantity",
        "unit", "source", "batchNo", "vendorName", "remarks"));
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillSchemaTest -q`
Expected: PASS — all 3 tests green.

- [ ] **Step 7: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillSchema.java backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillSchemaTest.java
git commit -m "feat(voice-fill): add role FK fields to DPR voice-fill schema

Add roleId, manpowerRoleRateId, equipmentRoleVariantId, materialRoleVariantId,
and per-row shift to the structured-output schema so the LLM can return rows
with the FKs the form needs to persist them."
```

---

### Task 2: Load Rate-Book Reference Data and Extend Prompt

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java`
- Create: `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java`

- [ ] **Step 1: Write the failing test for reference data loading**

Create `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java`:

```java
package com.bipros.ai.voice.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.ModelCapabilityRegistry;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.voice.SpeechToTextService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.application.dto.role.EquipmentRoleVariantResponse;
import com.bipros.resource.application.dto.role.ManpowerRoleRateResponse;
import com.bipros.resource.application.dto.role.MaterialRoleVariantResponse;
import com.bipros.resource.application.dto.role.RoleAssignmentResponse;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateService;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DprVoiceFillServiceTest {

    private SpeechToTextService speechToTextService;
    private LlmProviderConfigRepository providerConfigRepository;
    private OpenAiCompatibleProvider provider;
    private DprVoiceFillSchema schemaBuilder;
    private ObjectMapper objectMapper;
    private ResourceRepository resourceRepository;
    private ProjectResourceRepository projectResourceRepository;
    private ActivityRepository activityRepository;
    private BoqItemRepository boqItemRepository;
    private RoleRateService roleRateService;
    private RoleAssignmentService roleAssignmentService;
    private ModelCapabilityRegistry capabilityRegistry;
    private DprVoiceFillService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID manpowerRoleId = UUID.randomUUID();
    private final UUID manpowerVariantId = UUID.randomUUID();
    private final UUID equipmentRoleId = UUID.randomUUID();
    private final UUID equipmentVariantId = UUID.randomUUID();
    private final UUID materialRoleId = UUID.randomUUID();
    private final UUID materialVariantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        speechToTextService = mock(SpeechToTextService.class);
        providerConfigRepository = mock(LlmProviderConfigRepository.class);
        provider = mock(OpenAiCompatibleProvider.class);
        schemaBuilder = new DprVoiceFillSchema(new ObjectMapper());
        objectMapper = new ObjectMapper();
        resourceRepository = mock(ResourceRepository.class);
        projectResourceRepository = mock(ProjectResourceRepository.class);
        activityRepository = mock(ActivityRepository.class);
        boqItemRepository = mock(BoqItemRepository.class);
        roleRateService = mock(RoleRateService.class);
        roleAssignmentService = mock(RoleAssignmentService.class);
        capabilityRegistry = new ModelCapabilityRegistry();

        service = new DprVoiceFillService(
            speechToTextService, providerConfigRepository, provider, schemaBuilder,
            objectMapper, resourceRepository, projectResourceRepository,
            activityRepository, boqItemRepository,
            roleRateService, roleAssignmentService, capabilityRegistry);

        when(speechToTextService.transcribe(any(), any(), any())).thenReturn("5 masons, 1 JCB");
        when(providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue())
            .thenReturn(Optional.of(providerConfig("gpt-4o")));
        when(resourceRepository.findByResourceType_CodeAndStatus(any(), any()))
            .thenReturn(List.of());
        when(projectResourceRepository.findByProjectId(any())).thenReturn(List.of());
        when(activityRepository.findByProjectId(any())).thenReturn(List.of());
        when(boqItemRepository.findByProjectIdOrderByItemNoAsc(any())).thenReturn(List.of());
    }

    private LlmProviderConfig providerConfig(String model) {
        LlmProviderConfig cfg = new LlmProviderConfig();
        cfg.setModel(model);
        cfg.setMaxTokens(4096);
        cfg.setTimeoutMs(60000);
        return cfg;
    }

    private DprVoiceFillRequest requestWithActivity() {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("activityId", activityId.toString());
        return new DprVoiceFillRequest(state, List.of(), null);
    }

    @Test
    void loadsManpowerRolesFromRateBookAndPlannedAssignments() {
        // Planned assignment for the activity
        RoleAssignmentResponse planned = new RoleAssignmentResponse(
            UUID.randomUUID(), activityId, "Excavation", projectId,
            manpowerRoleId, "Mason", "MANPOWER", manpowerVariantId, "Grade I",
            5, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("500"), "DAY", "RATE", null, null, false);
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of(planned));

        // Rate book entry (different variant — rate-book-only)
        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            UUID.randomUUID(), manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade II", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));

        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());

        // LLM returns empty patch
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        // Verify the prompt sent to the LLM includes the manpower roles section
        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertTrue(prompt.contains("Manpower roles"), "Prompt must include manpower roles section");
        assertTrue(prompt.contains(manpowerVariantId.toString()),
            "Prompt must include the planned manpower variantId");
        assertTrue(prompt.contains(bookEntry.id().toString()),
            "Prompt must include the rate-book manpower variantId");
    }

    @Test
    void loadsEquipmentAndMaterialRolesFromRateBook() {
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of());

        EquipmentRoleVariantResponse eqBook = new EquipmentRoleVariantResponse(
            equipmentVariantId, equipmentRoleId, "JCB", "JCB", "3DX", "HOUR",
            new BigDecimal("800"), new BigDecimal("40"), true);
        when(roleRateService.listAllEquipment()).thenReturn(List.of(eqBook));

        MaterialRoleVariantResponse matBook = new MaterialRoleVariantResponse(
            materialVariantId, materialRoleId, "Aggregate", "20mm", "Cum",
            new BigDecimal("1200"), true);
        when(roleRateService.listAllMaterial()).thenReturn(List.of(matBook));

        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertTrue(prompt.contains("Equipment roles"), "Prompt must include equipment roles section");
        assertTrue(prompt.contains(equipmentVariantId.toString()));
        assertTrue(prompt.contains("Material roles"), "Prompt must include material roles section");
        assertTrue(prompt.contains(materialVariantId.toString()));
    }

    @Test
    void loadsRateBookEvenWhenActivityIdIsNull() {
        // No activity in state — should still load the global rate book
        ObjectNode state = objectMapper.createObjectNode();
        DprVoiceFillRequest req = new DprVoiceFillRequest(state, List.of(), null);

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            manpowerVariantId, manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade I", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), req);

        verify(roleRateService).listAllManpower();
        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertTrue(prompt.contains(manpowerVariantId.toString()),
            "Rate-book entries must appear in prompt even without activityId");
    }

    private org.springframework.web.multipart.MultipartFile emptyAudio() {
        org.springframework.web.multipart.MultipartFile audio =
            mock(org.springframework.web.multipart.MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getOriginalFilename()).thenReturn("audio.webm");
        when(audio.getContentType()).thenReturn("audio/webm");
        try {
            when(audio.getBytes()).thenReturn(new byte[]{0, 1, 2});
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return audio;
    }

    private LlmProvider.ChatResponse llmResponse(String content) {
        return new LlmProvider.ChatResponse(content, List.of(), null, "gpt-4o");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: FAIL — `DprVoiceFillService` constructor does not accept `RoleRateService`, `RoleAssignmentService`, `ModelCapabilityRegistry`. Compilation error.

- [ ] **Step 3: Add new imports to DprVoiceFillService**

In `DprVoiceFillService.java`, add these imports (after the existing `bipros.resource` imports, keeping the order: jakarta → org → java → com.bipros):

```java
import com.bipros.resource.application.dto.role.EquipmentRoleVariantResponse;
import com.bipros.resource.application.dto.role.ManpowerRoleRateResponse;
import com.bipros.resource.application.dto.role.MaterialRoleVariantResponse;
import com.bipros.resource.application.dto.role.RoleAssignmentResponse;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateService;
import com.bipros.ai.provider.ModelCapabilityRegistry;
```

- [ ] **Step 4: Inject new services and update constructor**

In `DprVoiceFillService.java`, add three new fields after the existing `boqItemRepository` field (around line 69):

```java
  private final RoleRateService roleRateService;
  private final RoleAssignmentService roleAssignmentService;
  private final ModelCapabilityRegistry capabilityRegistry;
```

The class uses `@RequiredArgsConstructor`, so Lombok auto-generates the constructor. The field order determines the constructor parameter order — new fields go last, matching the test's constructor call.

- [ ] **Step 5: Extend ReferenceData record and add new role ref records**

In `DprVoiceFillService.java`, replace the existing `ReferenceData` record (around line 360) and add new role ref records:

```java
  private record ReferenceData(
      List<SupervisorRef> supervisors,
      List<ActivityRef> activities,
      List<BoqItemRef> boqItems,
      List<ManpowerRoleRef> manpowerRoles,
      List<EquipmentRoleRef> equipmentRoles,
      List<MaterialRoleRef> materialRoles) {}

  private record SupervisorRef(String id, String name, String roleName) {}
  private record ActivityRef(String id, String code, String name) {}
  private record BoqItemRef(String itemNo, String description, String unit) {}

  private record ManpowerRoleRef(
      String variantId, String roleId, String roleName,
      String categoryName, String gradeName, boolean planned) {}

  private record EquipmentRoleRef(
      String variantId, String roleId, String roleName,
      String make, String model, boolean planned) {}

  private record MaterialRoleRef(
      String variantId, String roleId, String roleName,
      String specGrade, String unit, boolean planned) {}
```

- [ ] **Step 6: Update loadReferenceData signature and add role loaders**

In `DprVoiceFillService.java`, replace the existing `loadReferenceData` method (around line 303):

```java
  private ReferenceData loadReferenceData(UUID projectId, UUID activityId) {
    return new ReferenceData(
        loadSupervisors(projectId),
        loadActivities(projectId),
        loadBoqItems(projectId),
        loadManpowerRoles(activityId),
        loadEquipmentRoles(activityId),
        loadMaterialRoles(activityId));
  }
```

Add three new loader methods after `loadBoqItems` (before the `cap` method):

```java
  private List<ManpowerRoleRef> loadManpowerRoles(UUID activityId) {
    // Planned assignments first (when an activity is selected), then the full rate book.
    // Dedup by variantId so a planned item doesn't appear twice.
    java.util.Set<String> seen = new HashSet<>();
    java.util.List<ManpowerRoleRef> out = new ArrayList<>();

    if (activityId != null) {
      for (RoleAssignmentResponse a : roleAssignmentService.listForActivity(activityId)) {
        if (!"MANPOWER".equalsIgnoreCase(a.roleType())
            && !"LABOR".equalsIgnoreCase(a.roleType())) continue;
        if (a.unplanned()) continue;
        if (a.variantId() == null || a.roleId() == null) continue;
        String vid = a.variantId().toString();
        if (seen.contains(vid)) continue;
        seen.add(vid);
        out.add(new ManpowerRoleRef(vid, a.roleId().toString(), a.roleName(), null, null, true));
      }
    }

    for (ManpowerRoleRateResponse v : roleRateService.listAllManpower()) {
      String vid = v.id().toString();
      if (seen.contains(vid)) continue;
      seen.add(vid);
      out.add(new ManpowerRoleRef(vid, v.roleId().toString(), v.roleName(),
          v.categoryName(), v.gradeName(), false));
    }
    return cap(out);
  }

  private List<EquipmentRoleRef> loadEquipmentRoles(UUID activityId) {
    java.util.Set<String> seen = new HashSet<>();
    java.util.List<EquipmentRoleRef> out = new ArrayList<>();

    if (activityId != null) {
      for (RoleAssignmentResponse a : roleAssignmentService.listForActivity(activityId)) {
        if (!"EQUIPMENT".equalsIgnoreCase(a.roleType())) continue;
        if (a.unplanned()) continue;
        if (a.variantId() == null || a.roleId() == null) continue;
        String vid = a.variantId().toString();
        if (seen.contains(vid)) continue;
        seen.add(vid);
        out.add(new EquipmentRoleRef(vid, a.roleId().toString(), a.roleName(), null, null, true));
      }
    }

    for (EquipmentRoleVariantResponse v : roleRateService.listAllEquipment()) {
      String vid = v.id().toString();
      if (seen.contains(vid)) continue;
      seen.add(vid);
      out.add(new EquipmentRoleRef(vid, v.roleId().toString(), v.roleName(),
          v.make(), v.model(), false));
    }
    return cap(out);
  }

  private List<MaterialRoleRef> loadMaterialRoles(UUID activityId) {
    java.util.Set<String> seen = new HashSet<>();
    java.util.List<MaterialRoleRef> out = new ArrayList<>();

    if (activityId != null) {
      for (RoleAssignmentResponse a : roleAssignmentService.listForActivity(activityId)) {
        if (!"MATERIAL".equalsIgnoreCase(a.roleType())) continue;
        if (a.unplanned()) continue;
        if (a.variantId() == null || a.roleId() == null) continue;
        String vid = a.variantId().toString();
        if (seen.contains(vid)) continue;
        seen.add(vid);
        out.add(new MaterialRoleRef(vid, a.roleId().toString(), a.roleName(), null, null, true));
      }
    }

    for (MaterialRoleVariantResponse v : roleRateService.listAllMaterial()) {
      String vid = v.id().toString();
      if (seen.contains(vid)) continue;
      seen.add(vid);
      out.add(new MaterialRoleRef(vid, v.roleId().toString(), v.roleName(),
          v.specGrade(), v.unit(), false));
    }
    return cap(out);
  }
```

- [ ] **Step 7: Update fill() to extract activityId and pass it to loadReferenceData**

In `DprVoiceFillService.java`, in the `fill` method, replace the existing `loadReferenceData(projectId)` call (around line 88):

```java
    // Extract activityId from the form state so we can load activity-scoped planned
    // role assignments alongside the global rate book.
    String activityIdStr = request.state().path("activityId").asText(null);
    UUID activityId = tryParseUuid(activityIdStr);

    ReferenceData refs = loadReferenceData(projectId, activityId);
```

- [ ] **Step 8: Extend referenceDataPrompt with role sections**

In `DprVoiceFillService.java`, in `referenceDataPrompt()`, add three new sections after the BOQ items block (before the `return sb.toString();`):

```java
    sb.append("\nManpower roles (variantId — roleName [category/grade] (planned|rate-book)):\n");
    if (refs.manpowerRoles().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (ManpowerRoleRef m : refs.manpowerRoles()) {
        sb.append("  ").append(m.variantId()).append(" — ").append(m.roleName());
        if (m.categoryName() != null || m.gradeName() != null) {
          sb.append(" [");
          if (m.categoryName() != null) sb.append(m.categoryName());
          if (m.gradeName() != null) {
            if (m.categoryName() != null) sb.append("/");
            sb.append(m.gradeName());
          }
          sb.append("]");
        }
        sb.append(m.planned() ? " (planned)\n" : " (rate-book)\n");
      }
    }

    sb.append("\nEquipment roles (variantId — roleName make/model (planned|rate-book)):\n");
    if (refs.equipmentRoles().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (EquipmentRoleRef e : refs.equipmentRoles()) {
        sb.append("  ").append(e.variantId()).append(" — ").append(e.roleName());
        if (e.make() != null || e.model() != null) {
          sb.append(" ");
          if (e.make() != null) sb.append(e.make());
          if (e.model() != null) {
            if (e.make() != null) sb.append(" / ");
            sb.append(e.model());
          }
        }
        sb.append(e.planned() ? " (planned)\n" : " (rate-book)\n");
      }
    }

    sb.append("\nMaterial roles (variantId — roleName specGrade unit (planned|rate-book)):\n");
    if (refs.materialRoles().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (MaterialRoleRef m : refs.materialRoles()) {
        sb.append("  ").append(m.variantId()).append(" — ").append(m.roleName());
        if (m.specGrade() != null) sb.append(" ").append(m.specGrade());
        if (m.unit() != null) sb.append(" ").append(m.unit());
        sb.append(m.planned() ? " (planned)\n" : " (rate-book)\n");
      }
    }
```

- [ ] **Step 9: Update system prompt with role resolution instructions**

In `DprVoiceFillService.java`, in `systemPrompt()`, add these rules to the existing rules block (before the final `"""`):

```java
        - For manpower rows: set manpowerRoleRateId to the variantId from the Manpower roles
          list, roleId to the matching roleId, and trade to the role's roleName. When no exact
          match exists, set both ids to null and emit a follow-up question.
        - For equipment rows: set equipmentRoleVariantId to the variantId from the Equipment
          roles list, roleId to the matching roleId, and equipmentType to the role's roleName.
        - For material rows: set materialRoleVariantId to the variantId from the Material roles
          list, roleId to the matching roleId, and materialName to the role's roleName.
```

- [ ] **Step 10: Run test to verify it passes**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: PASS — all 3 tests green.

- [ ] **Step 11: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java
git commit -m "feat(voice-fill): load rate-book reference data for role resolution

Inject RoleRateService and RoleAssignmentService into DprVoiceFillService.
Load manpower/equipment/material rate-book entries (planned + global) as
reference data so the LLM can resolve free-text mentions to canonical UUIDs.
Extend the system prompt with role resolution instructions."
```

---

### Task 3: Validate Role IDs in validateAndAssemble

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java`
- Modify: `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java`

- [ ] **Step 1: Write the failing test for invalid role ID demotion**

Add to `DprVoiceFillServiceTest.java`:

```java
    @Test
    void demotesInvalidManpowerRoleVariantIdAndEmitsFollowUp() {
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of());
        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());

        // LLM returns a manpower row with a hallucinated variantId
        String fakeVariantId = UUID.randomUUID().toString();
        String fakeRoleId = UUID.randomUUID().toString();
        String llmJson = String.format("""
            {
              "patch": {
                "reportDate": null, "supervisorResourceId": null, "supervisorName": null,
                "activityId": null, "activityName": null, "contractorName": null,
                "weatherCondition": null, "startTime": null, "endTime": null,
                "shift": null, "approvalStatus": null, "side": null, "landmark": null,
                "chainageFromM": null, "chainageToM": null, "boqItemNo": null,
                "unit": null, "qtyExecuted": null, "remarks": null, "delayReason": null,
                "safetyObservation": null, "safetyIncidentType": null,
                "manpower": [{
                  "resourceAssignmentId": null, "roleId": "%s", "manpowerRoleRateId": "%s",
                  "trade": "Mason", "category": null, "shift": "DAY",
                  "nos": 5, "workingHours": 8, "otHours": null,
                  "contractorName": null, "remarks": null
                }],
                "equipment": [], "materials": []
              },
              "photoCaptions": [],
              "followUpQuestion": null,
              "complete": true,
              "assistantMessage": "Got it."
            }
            """, fakeRoleId, fakeVariantId);
        when(provider.chat(any(), any())).thenReturn(llmResponse(llmJson));

        DprVoiceFillResponse response = service.fill(projectId, emptyAudio(), requestWithActivity());

        // The invalid variantId must be demoted to null
        JsonNode manpowerRow = response.patch().path("manpower").get(0);
        assertTrue(manpowerRow.path("manpowerRoleRateId").isNull(),
            "Invalid manpowerRoleRateId must be demoted to null");
        assertTrue(manpowerRow.path("roleId").isNull(),
            "Invalid roleId must be demoted to null");
        // A follow-up question must be synthesized
        assertNotNull(response.followUpQuestion(), "Follow-up question must be emitted for invalid role");
        assertTrue(response.followUpQuestion().toLowerCase().contains("manpower"),
            "Follow-up must mention manpower");
        assertFalse(response.complete(), "complete must be false when a demotion occurs");
    }

    @Test
    void keepsValidManpowerRoleVariantId() {
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of());

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            manpowerVariantId, manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade I", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());

        String llmJson = String.format("""
            {
              "patch": {
                "reportDate": null, "supervisorResourceId": null, "supervisorName": null,
                "activityId": null, "activityName": null, "contractorName": null,
                "weatherCondition": null, "startTime": null, "endTime": null,
                "shift": null, "approvalStatus": null, "side": null, "landmark": null,
                "chainageFromM": null, "chainageToM": null, "boqItemNo": null,
                "unit": null, "qtyExecuted": null, "remarks": null, "delayReason": null,
                "safetyObservation": null, "safetyIncidentType": null,
                "manpower": [{
                  "resourceAssignmentId": null, "roleId": "%s", "manpowerRoleRateId": "%s",
                  "trade": "Mason", "category": "SKILLED", "shift": "DAY",
                  "nos": 5, "workingHours": 8, "otHours": null,
                  "contractorName": null, "remarks": null
                }],
                "equipment": [], "materials": []
              },
              "photoCaptions": [],
              "followUpQuestion": null,
              "complete": true,
              "assistantMessage": "Got it."
            }
            """, manpowerRoleId.toString(), manpowerVariantId.toString());
        when(provider.chat(any(), any())).thenReturn(llmResponse(llmJson));

        DprVoiceFillResponse response = service.fill(projectId, emptyAudio(), requestWithActivity());

        JsonNode manpowerRow = response.patch().path("manpower").get(0);
        assertEquals(manpowerVariantId.toString(), manpowerRow.path("manpowerRoleRateId").asText(),
            "Valid manpowerRoleRateId must be preserved");
        assertEquals(manpowerRoleId.toString(), manpowerRow.path("roleId").asText(),
            "Valid roleId must be preserved");
        assertNull(response.followUpQuestion(), "No follow-up when all IDs are valid");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: FAIL — `demotesInvalidManpowerRoleVariantIdAndEmitsFollowUp` fails because validation does not yet check role IDs. `keepsValidManpowerRoleVariantId` may also fail if the validation code isn't there at all (no demotion means the invalid UUID passes through unchanged).

- [ ] **Step 3: Add role ID validation to validateAndAssemble**

In `DprVoiceFillService.java`, in `validateAndAssemble()`, after the existing BOQ validation block (around line 247) and before the `if (!demoted.isEmpty()...)` block, add:

```java
    // Validate role FK fields in row arrays against the loaded reference data.
    Set<String> validManpowerVariantIds = new HashSet<>();
    for (ManpowerRoleRef m : refs.manpowerRoles()) validManpowerVariantIds.add(m.variantId());
    Set<String> validEquipmentVariantIds = new HashSet<>();
    for (EquipmentRoleRef e : refs.equipmentRoles()) validEquipmentVariantIds.add(e.variantId());
    Set<String> validMaterialVariantIds = new HashSet<>();
    for (MaterialRoleRef m : refs.materialRoles()) validMaterialVariantIds.add(m.variantId());

    demoteInvalidRowIds(patch.path("manpower"), "manpowerRoleRateId", "roleId",
        validManpowerVariantIds, "manpower role", demoted);
    demoteInvalidRowIds(patch.path("equipment"), "equipmentRoleVariantId", "roleId",
        validEquipmentVariantIds, "equipment type", demoted);
    demoteInvalidRowIds(patch.path("materials"), "materialRoleVariantId", "roleId",
        validMaterialVariantIds, "material", demoted);
```

Add the helper method after `validateAndAssemble`:

```java
  /**
   * For each row in the array, if the variantId field doesn't match a valid UUID from the
   * reference data, null out both the variantId and roleId fields and add a demotion label.
   * This prevents hallucinated UUIDs from reaching the form's save filter.
   */
  private void demoteInvalidRowIds(JsonNode rows, String variantIdField, String roleIdField,
      Set<String> validVariantIds, String demotionLabel, List<String> demoted) {
    if (!rows.isArray()) return;
    for (JsonNode row : rows) {
      if (!(row instanceof ObjectNode obj)) continue;
      JsonNode vidNode = obj.path(variantIdField);
      if (vidNode.isNull()) continue;
      String vid = vidNode.asText(null);
      if (vid == null || !validVariantIds.contains(vid)) {
        obj.putNull(variantIdField);
        obj.putNull(roleIdField);
        if (!demoted.contains(demotionLabel)) demoted.add(demotionLabel);
      }
    }
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: PASS — both new tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java
git commit -m "feat(voice-fill): validate role FK IDs and demote hallucinated UUIDs

validateAndAssemble now checks manpowerRoleRateId, equipmentRoleVariantId,
and materialRoleVariantId against the loaded reference data. Invalid UUIDs
are demoted to null and a follow-up question is synthesized so the user can
manually pick the correct entry."
```

---

### Task 4: Fix Temperature for Reasoning Models

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java`
- Modify: `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add to `DprVoiceFillServiceTest.java`:

```java
    @Test
    void sendsNullTemperatureForReasoningModel() {
        when(providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue())
            .thenReturn(Optional.of(providerConfig("gpt-5.4")));
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        assertNull(captor.getValue().temperature(),
            "Reasoning models (gpt-5*) must receive null temperature");
    }

    @Test
    void sendsLowTemperatureForNonReasoningModel() {
        when(providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue())
            .thenReturn(Optional.of(providerConfig("gpt-4o")));
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        assertEquals(0.1, captor.getValue().temperature(),
            "Non-reasoning models must receive temperature=0.1");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: FAIL — `sendsNullTemperatureForReasoningModel` fails because `buildChatRequest` always sends `0.1`.

- [ ] **Step 3: Fix temperature in buildChatRequest**

In `DprVoiceFillService.java`, in `buildChatRequest()`, change the `ChatRequest` construction. Replace the hardcoded `0.1` temperature with a model-aware value. The method needs access to the provider config's model — add a `model` parameter:

Change the method signature from:
```java
  private LlmProvider.ChatRequest buildChatRequest(
      String transcript, ReferenceData refs, DprVoiceFillRequest req) {
```
to:
```java
  private LlmProvider.ChatRequest buildChatRequest(
      String transcript, ReferenceData refs, DprVoiceFillRequest req, String model) {
```

Inside the method, before the `ChatRequest` construction, add:
```java
    ModelCapabilityRegistry.Capabilities caps = capabilityRegistry.forModel(model);
    Double temperature = caps.reasoningModel() ? null : 0.1;
```

Change the `ChatRequest` return from:
```java
    return new LlmProvider.ChatRequest(
        messages,
        null,        // no tools — pure structured output
        1024,        // maxTokens
        0.1,         // low temperature: factual extraction, not creative writing
        45_000L,     // 45 s timeout
        responseFormat);
```
to:
```java
    return new LlmProvider.ChatRequest(
        messages,
        null,        // no tools — pure structured output
        1024,        // maxTokens
        temperature, // null for reasoning models (gpt-5*), 0.1 for non-reasoning
        45_000L,     // 45 s timeout
        responseFormat);
```

Update the call site in `fill()` — change:
```java
    LlmProvider.ChatRequest chatRequest = buildChatRequest(transcript, refs, request);
```
to:
```java
    LlmProvider.ChatRequest chatRequest = buildChatRequest(transcript, refs, request, cfg.getModel());
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest -q`
Expected: PASS — all tests green (both temperature tests + all prior tests).

- [ ] **Step 5: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java
git commit -m "fix(voice-fill): send null temperature for reasoning models

gpt-5 series reasoning models reject non-default temperatures via the Chat
Completions API. Use ModelCapabilityRegistry to detect reasoning models and
send null temperature for them, 0.1 for non-reasoning models. This mirrors
the existing testConnection behavior."
```

---

### Task 5: Add Success Logging

**Files:**
- Modify: `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java`

- [ ] **Step 1: Add summary log line after validateAndAssemble**

In `DprVoiceFillService.java`, in `fill()`, after the `return validateAndAssemble(...)` line, there is no further code (it's a single return). Change the end of `fill()` from:

```java
    return validateAndAssemble(transcript, root, refs);
```

to:

```java
    DprVoiceFillResponse response = validateAndAssemble(transcript, root, refs);
    JsonNode patch = response.patch();
    log.info("[dpr voice-fill] project={} complete={} manpowerRows={} equipmentRows={} materialRows={} followUp={}",
        projectId, response.complete(),
        patch.path("manpower").size(),
        patch.path("equipment").size(),
        patch.path("materials").size(),
        response.followUpQuestion());
    return response;
```

- [ ] **Step 2: Run all backend tests to verify nothing broke**

Run: `mvn test -pl bipros-ai -Dtest=DprVoiceFillServiceTest,DprVoiceFillSchemaTest -q`
Expected: PASS — all tests green.

- [ ] **Step 3: Commit**

```bash
git add backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java
git commit -m "feat(voice-fill): add success summary log line

Log patch field counts (manpower/equipment/material rows), completion status,
and follow-up question after each voice-fill call for debugging without
needing DEBUG SQL logging."
```

---

### Task 6: Tighten Frontend Patch Row Types

**Files:**
- Modify: `frontend/src/lib/api/dprApi.ts`
- Modify: `frontend/src/components/dpr/DprActivityForm.tsx`

- [ ] **Step 1: Tighten DprVoicePatch row types**

In `frontend/src/lib/api/dprApi.ts`, add the import for DPR row types (near the top, after existing imports):

```typescript
import type { DprManpowerRow, DprEquipmentRow, DprMaterialRow } from "@/lib/types/dpr";
```

Change the `DprVoicePatch` interface's row array fields from:

```typescript
  manpower?: Array<Record<string, unknown>>;
  equipment?: Array<Record<string, unknown>>;
  materials?: Array<Record<string, unknown>>;
```

to:

```typescript
  manpower?: Array<Partial<DprManpowerRow>>;
  equipment?: Array<Partial<DprEquipmentRow>>;
  materials?: Array<Partial<DprMaterialRow>>;
```

- [ ] **Step 2: Remove unsafe casts in applyVoicePatch**

In `frontend/src/components/dpr/DprActivityForm.tsx`, in `applyVoicePatch`, replace the three row-append blocks from:

```typescript
      if (Array.isArray(patch.manpower) && patch.manpower.length > 0) {
        next.manpower = [
          ...(current.manpower ?? []),
          ...(patch.manpower as unknown as DprManpowerRow[]),
        ];
      }
      if (Array.isArray(patch.equipment) && patch.equipment.length > 0) {
        next.equipment = [
          ...(current.equipment ?? []),
          ...(patch.equipment as unknown as DprEquipmentRow[]),
        ];
      }
      if (Array.isArray(patch.materials) && patch.materials.length > 0) {
        next.materials = [
          ...(current.materials ?? []),
          ...(patch.materials as unknown as DprMaterialRow[]),
        ];
      }
```

to:

```typescript
      if (Array.isArray(patch.manpower) && patch.manpower.length > 0) {
        next.manpower = [
          ...(current.manpower ?? []),
          ...patch.manpower,
        ];
      }
      if (Array.isArray(patch.equipment) && patch.equipment.length > 0) {
        next.equipment = [
          ...(current.equipment ?? []),
          ...patch.equipment,
        ];
      }
      if (Array.isArray(patch.materials) && patch.materials.length > 0) {
        next.materials = [
          ...(current.materials ?? []),
          ...patch.materials,
        ];
      }
```

Note: `Partial<DprManpowerRow>` is assignable to `DprManpowerRow[]` via spread because TypeScript widens — but if the compiler complains about `trade` being possibly undefined, cast the spread items: `...(patch.manpower as DprManpowerRow[])`. The rows from the backend always include `trade`/`equipmentType`/`materialName` since those are `required` in the schema.

- [ ] **Step 3: Run lint and typecheck**

Run: `cd frontend && pnpm lint && pnpm tsc --noEmit`
Expected: PASS — no errors. If TypeScript complains about `Partial<DprManpowerRow>` not being assignable to `DprManpowerRow`, revert to `...(patch.manpower as DprManpowerRow[])` — the runtime behavior is identical, the cast just satisfies the compiler.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api/dprApi.ts frontend/src/components/dpr/DprActivityForm.tsx
git commit -m "refactor(voice-fill): tighten DprVoicePatch row types and remove unsafe casts

Replace Array<Record<string, unknown>> with Array<Partial<DprManpowerRow>> etc.
so the patch rows carry the FK fields (manpowerRoleRateId, roleId) the form
needs to survive the submit filter."
```

---

### Task 7: Add User Feedback Toasts in DprVoiceAssistant

**Files:**
- Modify: `frontend/src/components/dpr/DprVoiceAssistant.tsx`

- [ ] **Step 1: Add toast import and success/follow-up feedback**

In `frontend/src/components/dpr/DprVoiceAssistant.tsx`, add the toast import near the top:

```typescript
import toast from "react-hot-toast";
```

In `sendRecording`, after the `applyPatch(result.patch)` line, add summary toast logic:

```typescript
      // Merge patch into the form state via the parent callback.
      if (result.patch) applyPatch(result.patch);

      // Summarize what was filled so the user gets immediate feedback.
      const filledFields: string[] = [];
      const p = result.patch;
      if (p.supervisorName) filledFields.push("supervisor");
      if (p.activityName) filledFields.push("activity");
      if (p.boqItemNo) filledFields.push("BOQ item");
      if (p.qtyExecuted != null) filledFields.push("quantity");
      if (Array.isArray(p.manpower) && p.manpower.length > 0)
        filledFields.push(`${p.manpower.length} manpower row(s)`);
      if (Array.isArray(p.equipment) && p.equipment.length > 0)
        filledFields.push(`${p.equipment.length} equipment row(s)`);
      if (Array.isArray(p.materials) && p.materials.length > 0)
        filledFields.push(`${p.materials.length} material row(s)`);

      if (filledFields.length > 0) {
        toast.success(`Voice fill: ${filledFields.join(", ")}`);
      }

      if (result.complete && !result.followUpQuestion) {
        toast.success("Form looks complete — review and save.");
      }
```

- [ ] **Step 2: Run lint and typecheck**

Run: `cd frontend && pnpm lint && pnpm tsc --noEmit`
Expected: PASS — no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/dpr/DprVoiceAssistant.tsx
git commit -m "feat(voice-fill): add toast feedback for filled fields

Show a success toast summarizing what the voice fill captured (supervisor,
activity, N manpower rows, etc.) and a 'form looks complete' toast when the
LLLM signals completeness."
```

---

### Task 8: Full Build and Smoke Test

**Files:** None (verification only)

- [ ] **Step 1: Run full backend build**

Run: `cd backend && mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Run all backend AI tests**

Run: `cd backend && mvn test -pl bipros-ai -q`
Expected: All tests pass.

- [ ] **Step 3: Run frontend build**

Run: `cd frontend && pnpm build`
Expected: Build succeeds.

- [ ] **Step 4: Restart backend with KEK and smoke test**

Run:
```bash
kill <backend-pid>
export BIPROS_AI_KEK='Vd/RdHKwlLA1vFuDVUr/ou0CMHAsha99Cfi8UXzXUlA='
cd backend && mvn -pl bipros-api spring-boot:run > /tmp/bipros-logs/backend.log 2>&1 &
```

Wait for startup, then verify the log shows:
```
ApiKeyCipher initialized with provided KEK.
Started BiprosApplication in ... seconds
```

- [ ] **Step 5: Manual voice-fill test**

1. Open http://localhost:3001 in the browser
2. Navigate to a project → DPR → Add DPR
3. Pick an activity first (so planned role assignments load)
4. Click "Voice fill", speak: "supervisor Ramesh, 5 masons skilled, 8 hours, 1 JCB excavator, 20 cum aggregate 20mm"
5. Stop recording
6. Verify: toast appears with filled fields, manpower/equipment/material rows appear in the form with the correct dropdown entries selected
7. Click Save
8. Verify: rows survive save (they don't vanish)
9. Check backend log for the summary line: `[dpr voice-fill] project=... complete=true manpowerRows=1 equipmentRows=1 materialRows=1`

- [ ] **Step 6: Final commit (if any fixes were needed during smoke test)**

Only if fixes were needed:
```bash
git add -A
git commit -m "fix(voice-fill): smoke test fixes"
```
