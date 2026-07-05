# Voice-Fill Manpower/Equipment/Material Row Resolution

**Date:** 2026-07-05
**Status:** Approved
**Depends on:** existing voice-fill pipeline (`DprVoiceFillController`, `DprVoiceFillService`, `DprVoiceFillSchema`, `DprVoiceAssistant`)

## Problem

The DPR voice-fill feature is wired end-to-end (mic capture -> Whisper transcription -> LLM structured output -> patch merge) but **manpower, equipment, and material rows filled by voice vanish on save**. The root cause is a three-layer gap:

1. **No reference data for roles.** `DprVoiceFillService.loadReferenceData` loads supervisors, activities, and BOQ items only. It never loads the manpower/equipment/material rate book or the activity's planned role assignments. The LLM has no list to resolve "5 masons" against.

2. **Schema omits the actual FK fields.** `DprVoiceFillSchema` row definitions include `resourceAssignmentId` + free-text fields (`trade`, `equipmentType`, `materialName`) but not `roleId`, `manpowerRoleRateId`, `equipmentRoleVariantId`, or `materialRoleVariantId` -- the FKs the form needs to persist a row.

3. **Frontend silently drops FK-less rows.** `DprActivityForm.handleSubmit` filters out any row without `manpowerRoleRateId`/`equipmentRoleVariantId`/`materialRoleVariantId`/`resourceAssignmentId` (`DprActivityForm.tsx:653-661`). Voice-filled rows carry none of these, so they are silently discarded.

**Secondary issue:** `DprVoiceFillService.buildChatRequest` sends `temperature=0.1`. For gpt-5 series reasoning models, the Chat Completions API may reject non-default temperatures. The `testConnection` method already sends `null` temperature for this reason (comment at `OpenAiCompatibleProvider.java:272-274`), but voice-fill does not.

## Solution

Extend the voice-fill pipeline to load rate-book reference data for manpower/equipment/material roles, add the role FK fields to the structured-output schema, validate returned IDs, and fix the temperature for reasoning models.

## Architecture

### Backend Changes

#### 1. `DprVoiceFillService` -- Reference Data Extension

**New dependencies:** inject `RoleRateService` and `RoleAssignmentService` (both in `bipros-resource`). `bipros-ai` already depends on `bipros-resource`.

**Extract activityId from form state:** In `fill()`, before calling `loadReferenceData`, extract the activityId from the request state:
```java
String activityIdStr = request.state().path("activityId").asText(null);
UUID activityId = tryParseUuid(activityIdStr);
ReferenceData refs = loadReferenceData(projectId, activityId);
```
Change `loadReferenceData` signature from `(UUID projectId)` to `(UUID projectId, UUID activityId)`.

**New reference data loaders** (mirror the frontend `ManpowerGrid`/`EquipmentGrid`/`MaterialGrid` logic):

- `loadManpowerRoles(UUID activityId)` -- `activityId` may be `null` (when the form state has no activity set yet):
  - Call `roleAssignmentService.listForActivity(activityId)` -- filter `roleType == "MANPOWER" || roleType == "LABOR"`, exclude `unplanned == true`
  - Call `roleRateService.listAllManpower()` -- the global rate book
  - Dedup by `(roleId, variantId)`, planned entries first
  - Map to `ManpowerRoleRef(variantId, roleId, roleName, categoryName, gradeName, planned)`
  - Cap at `MAX_REFERENCE_LIST_SIZE` (200)

- `loadEquipmentRoles(UUID activityId)`:
  - Same pattern, filter `roleType == "EQUIPMENT"`
  - `roleRateService.listAllEquipment()`
  - Map to `EquipmentRoleRef(variantId, roleId, roleName, make, model, planned)`

- `loadMaterialRoles(UUID activityId)`:
  - Same pattern, filter `roleType == "MATERIAL"`
  - `roleRateService.listAllMaterial()`
  - Map to `MaterialRoleRef(variantId, roleId, roleName, specGrade, unit, planned)`

**Chicken-and-egg:** When the user speaks the entire DPR in one turn ("supervisor Ramesh, activity earthwork, 5 masons..."), the activity may not be set in the form state yet. The LLM first resolves the activity from the activity list, then needs the manpower roles for that activity. Since the LLM gets all reference data in a single prompt, we load the rate book (global, not activity-scoped) so the LLM can always match role names. Planned assignments are loaded only when `state.activityId` is set. If the activity is not yet set, the LLM matches against the rate book alone and the row's `planned` flag is false -- the backend creates a phantom assignment on save (same as when the user picks an unplanned role manually).

**Extend `ReferenceData` record:**
```java
private record ReferenceData(
    List<SupervisorRef> supervisors,
    List<ActivityRef> activities,
    List<BoqItemRef> boqItems,
    List<ManpowerRoleRef> manpowerRoles,
    List<EquipmentRoleRef> equipmentRoles,
    List<MaterialRoleRef> materialRoles) {}
```

**Extend `referenceDataPrompt`** with three new sections after the BOQ items section:
```
Manpower roles (variantId — roleName [category/grade] (planned|rate-book)):
  <uuid> — Mason [Skilled/Grade I] (planned)
  <uuid> — Mason [Skilled/Grade II] (rate-book)
  ...
Equipment roles (variantId — roleName make/model (planned|rate-book)):
  <uuid> — Excavator [JCB / 3DX] (planned)
  ...
Material roles (variantId — roleName specGrade unit (planned|rate-book)):
  <uuid> — Cement [OPC 53 Grade] Bags (planned)
  ...
```

**Update system prompt** to instruct the LLM:
- "For manpower/equipment/material rows, set `manpowerRoleRateId`/`equipmentRoleVariantId`/`materialRoleVariantId` to the variantId from the provided role list. Set `roleId` to the matching roleId. When no exact match exists, set both to null and emit a follow-up question."
- "Set `trade`/`equipmentType`/`materialName` to the role's `roleName` from the reference list."

#### 2. `DprVoiceFillSchema` -- Row Schema Extension

**Manpower row** -- add fields:
- `roleId` -- nullable UUID string ("UUID of the manpower role, from the reference list.")
- `manpowerRoleRateId` -- nullable UUID string ("UUID of the manpower rate variant (variantId from the reference list). Must match a listed variantId.")
- `shift` -- nullable enum `["DAY", "NIGHT"]` ("Per-row shift. Default DAY when not stated.")

**Equipment row** -- add fields:
- `roleId` -- nullable UUID string
- `equipmentRoleVariantId` -- nullable UUID string ("UUID of the equipment variant (variantId from the reference list).")
- `shift` -- nullable enum `["DAY", "NIGHT"]`

**Material row** -- add fields:
- `roleId` -- nullable UUID string
- `materialRoleVariantId` -- nullable UUID string ("UUID of the material variant (variantId from the reference list).")

**Update `requireAll`** lists for each row type to include the new fields.

#### 3. `DprVoiceFillService.validateAndAssemble` -- ID Validation

For each row in `patch.manpower`:
- Check `manpowerRoleRateId` against `Set<UUID> validManpowerVariantIds` (from `refs.manpowerRoles()`)
- If invalid or absent: set `manpowerRoleRateId` to null, set `roleId` to null, add "manpower role" to `demoted`

Same for `patch.equipment` (`equipmentRoleVariantId` vs `validEquipmentVariantIds`) and `patch.materials` (`materialRoleVariantId` vs `validMaterialVariantIds`).

If any demotions occurred and no follow-up was set, synthesize: "I couldn't match the [manpower role / equipment type / material] against the project's rate book. Could you clarify?"

#### 4. `DprVoiceFillService.buildChatRequest` -- Temperature Fix

Inject `ModelCapabilityRegistry`. Resolve the provider config's model before building the request:
```java
Capabilities caps = capabilityRegistry.forModel(cfg.getModel());
Double temperature = caps.reasoningModel() ? null : 0.1;
```
Pass `temperature` into the `ChatRequest` instead of the hardcoded `0.1`.

This prevents gpt-5 series models from rejecting `temperature=0.1` via the Chat Completions API. The `testConnection` method already does this (sends `null`), and the Responses API path already drops temperature for reasoning models (`OpenAiCompatibleProvider.java:602-604`).

#### 5. `DprVoiceFillService.fill` -- Success Logging

After `validateAndAssemble`, log a summary line:
```java
log.info("[dpr voice-fill] project={} complete={} manpowerRows={} equipmentRows={} materialRows={} followUp={}",
    projectId, response.complete(),
    patch.path("manpower").size(), patch.path("equipment").size(), patch.path("materials").size(),
    response.followUpQuestion());
```
This makes it possible to debug voice-fill calls without enabling DEBUG SQL logging.

### Frontend Changes

#### 6. `dprApi.ts` -- Tighten Patch Row Types

Change `DprVoicePatch` row arrays from `Array<Record<string, unknown>>` to:
```typescript
manpower?: Array<Partial<DprManpowerRow>>;
equipment?: Array<Partial<DprEquipmentRow>>;
materials?: Array<Partial<DprMaterialRow>>;
```

Import the row types from `@/lib/types/dpr`.

#### 7. `DprActivityForm.applyVoicePatch` -- Remove Unsafe Casts

The existing append logic:
```typescript
next.manpower = [...(current.manpower ?? []), ...(patch.manpower as unknown as DprManpowerRow[])];
```
becomes:
```typescript
next.manpower = [...(current.manpower ?? []), ...(patch.manpower ?? [])];
```
Since the patch rows now carry `manpowerRoleRateId` + `roleId` (from the backend schema), the rows will have the FKs set and survive the submit filter at `handleSubmit` (`!!r.manpowerRoleRateId || !!r.resourceAssignmentId`). No logic change needed in the filter.

#### 8. `DprVoiceAssistant` -- User Feedback

After `applyPatch(result.patch)` in `sendRecording`:
- Count the non-null patch fields and rows
- Show `toast.success(...)` summarizing what was filled: e.g., "Voice fill: supervisor, activity, BOQ item, 2 manpower rows, 1 equipment row"
- If `result.followUpQuestion`, the existing follow-up banner is shown -- make it more prominent (add a subtle pulse animation and a "Tap mic to answer" hint)
- If `result.complete`, show `toast.success("Form looks complete -- review and save.")`

### Testing

#### Backend

**`DprVoiceFillServiceTest`** (new or extended, `@ExtendWith(MockitoExtension.class)`):
- Mock `RoleRateService.listAllManpower()` to return 3 entries; mock `RoleAssignmentService.listForActivity(activityId)` to return 2 planned (MANPOWER) + 1 planned (EQUIPMENT)
- Verify `loadReferenceData` produces a `ReferenceData` with 3 manpower roles (2 planned first, 1 rate-book-only) and 1 equipment role
- Verify `referenceDataPrompt` includes the manpower/equipment/material sections with UUIDs
- Mock LLM returns a patch with `manpower: [{ trade: "Mason", manpowerRoleRateId: <valid-uuid>, roleId: <valid-uuid>, nos: 5, workingHours: 8 }]`
- Verify `validateAndAssemble` keeps the row (FK survives validation)
- Mock LLM returns `manpowerRoleRateId: <invalid-uuid>` -> verify it is demoted to null + follow-up is set
- Verify reasoning model (gpt-5 prefix) -> `temperature=null`; non-reasoning (gpt-4o) -> `temperature=0.1`

**`DprVoiceFillSchemaTest`** (new):
- Verify `buildSchema()` produces a schema where manpower row `properties` includes `roleId`, `manpowerRoleRateId`, `shift`
- Same for equipment (`equipmentRoleVariantId`, `shift`) and materials (`materialRoleVariantId`)
- Verify all new fields are in the `required` array

#### Frontend

**`DprActivityForm` test** (if test infrastructure exists, else manual):
- `applyVoicePatch({ manpower: [{ trade: "Mason", manpowerRoleRateId: "uuid", roleId: "uuid", nos: 5 }] })`
- Verify `state.manpower` has 1 row with `manpowerRoleRateId` set
- Verify `handleSubmit`'s filter does NOT drop the row

## Data Flow

```
User speaks: "Supervisor Ramesh, activity earthwork excavation, 5 masons, 1 JCB, 20 cum aggregate"
  |
  v
Frontend (DprVoiceAssistant) records audio, sends {audio, state, history} to POST /v1/projects/{id}/dpr/voice-fill
  |
  v
Backend (DprVoiceFillService.fill):
  1. Whisper transcribes -> "Supervisor Ramesh, activity earthwork excavation, 5 masons, 1 JCB, 20 cum aggregate"
  2. loadReferenceData(projectId):
     - supervisors (existing)
     - activities (existing)
     - boqItems (existing)
     - manpowerRoles (NEW: planned + rate book)
     - equipmentRoles (NEW: planned + rate book)
     - materialRoles (NEW: planned + rate book)
  3. buildChatRequest with reference data + state + history + transcript
     - temperature=null for reasoning models (NEW)
  4. provider.chat(cfg, chatRequest) -> LLM returns structured JSON:
     {
       "patch": {
         "supervisorResourceId": "<uuid>", "supervisorName": "Ramesh",
         "activityId": "<uuid>", "activityName": "Earthwork Excavation",
         "manpower": [{ "trade": "Mason", "roleId": "<uuid>", "manpowerRoleRateId": "<uuid>", "nos": 5, "workingHours": 8, "shift": "DAY" }],
         "equipment": [{ "equipmentType": "JCB", "roleId": "<uuid>", "equipmentRoleVariantId": "<uuid>", "nos": 1, "workingHours": 8 }],
         "materials": [{ "materialName": "Aggregate 20mm", "roleId": "<uuid>", "materialRoleVariantId": "<uuid>", "quantity": 20, "unit": "Cum" }]
       },
       "complete": true, "followUpQuestion": null, "assistantMessage": "Got it..."
     }
  5. validateAndAssemble: checks all UUIDs against reference data -> all valid -> no demotions
  6. Returns DprVoiceFillResponse with patch + complete=true
  |
  v
Frontend (DprVoiceAssistant.sendRecording):
  - applyPatch(result.patch) -> DprActivityForm merges into state
  - manpower/equipment/material rows now have FKs set -> survive handleSubmit filter
  - toast.success("Voice fill: supervisor, activity, 2 manpower rows, 1 equipment row, 1 material row")
```

## Error Handling

- **LLM returns invalid variantId:** demoted to null, follow-up question synthesized. Row still appears in the form with free-text `trade`/`equipmentType`/`materialName` but no FK -- the user can manually pick the correct dropdown entry. The row is dropped on save if the user doesn't resolve it (same as a manually-added skeleton row).
- **No activity set in state:** rate book loaded globally (no planned entries). LLM matches against rate book only. Rows have `planned=false`; backend creates phantom assignments on save (existing behavior for unplanned picks).
- **Rate book empty:** reference data prompt shows "(none)" for the section. LLM emits a follow-up asking the user to add roles via the Resource Plan first.
- **LLM call fails:** existing `BusinessRuleException("VOICE_LLM_FAILED", ...)` propagates to the frontend; `DprVoiceAssistant` shows the error in the panel.
- **Reasoning model + temperature:** `null` temperature sent; no 400 from the API.

## Out of Scope

- Sub-contractor rows (use a different FK model: `activitySubContractorAssignmentId` -- could be added in a follow-up)
- Issue rows (free-text, no FK resolution needed)
- Photo captions (already handled by the existing schema)
- Streaming/transcription improvements (Whisper quality is upstream)
- Caching reference data (volumes are small, loaded fresh per call -- same as existing supervisors/activities/BOQ)

## Files Changed

**Backend:**
- `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillService.java` -- inject services, load role reference data, extend prompt, validate IDs, fix temperature, add success log
- `backend/bipros-ai/src/main/java/com/bipros/ai/voice/dpr/DprVoiceFillSchema.java` -- add role FK fields + shift to row schemas
- `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillServiceTest.java` -- new test
- `backend/bipros-ai/src/test/java/com/bipros/ai/voice/dpr/DprVoiceFillSchemaTest.java` -- new test

**Frontend:**
- `frontend/src/lib/api/dprApi.ts` -- tighten `DprVoicePatch` row types
- `frontend/src/components/dpr/DprActivityForm.tsx` -- remove unsafe casts in `applyVoicePatch`
- `frontend/src/components/dpr/DprVoiceAssistant.tsx` -- add toast feedback

## Risks

- **Prompt size:** adding 3 rate-book sections (up to 200 entries each) increases the prompt by ~15-20k tokens. This is within the 200k token conversation limit but could be slow/costly. Mitigation: cap at 200 per type (existing `MAX_REFERENCE_LIST_SIZE`); if the rate book is huge, consider fuzzy pre-filtering by the transcript (future enhancement).
- **LLM hallucination of UUIDs:** the validation step catches invalid UUIDs and demotes them. The user sees a follow-up question and can manually pick the right entry.
- **Model-specific behavior:** gpt-5's structured output support is confirmed via `ModelCapabilityRegistry` (`supportsResponseFormat=true`). If a future model doesn't support it, the schema enforcement will fail and the LLM will return free-text -- the existing `VOICE_LLM_BAD_JSON` error handles this.
