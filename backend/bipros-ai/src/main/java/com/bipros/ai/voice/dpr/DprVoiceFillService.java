package com.bipros.ai.voice.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.ModelCapabilityRegistry;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.voice.SpeechToTextService;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.project.application.dto.BoqItemResponse;
import com.bipros.project.application.service.BoqService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.application.dto.role.EquipmentRoleVariantResponse;
import com.bipros.resource.application.dto.role.ManpowerRoleRateResponse;
import com.bipros.resource.application.dto.role.MaterialRoleVariantResponse;
import com.bipros.resource.application.dto.role.RoleAssignmentResponse;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateService;
import com.bipros.security.application.dto.UserResponse;
import com.bipros.security.application.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates a single voice form-fill turn:
 * <ol>
 *   <li>Whisper-transcribe the audio.</li>
 *   <li>Load reference data (eligible supervisors, project activities, BOQ items) so the LLM
 *       can resolve free-text mentions to canonical UUIDs.</li>
 *   <li>Build a chat request with a system prompt + reference data + the form's current state +
 *       prior turns + the new transcript.</li>
 *   <li>Validate the structured response against the canonical lists, demoting any unresolvable
 *       reference to a follow-up question.</li>
 * </ol>
 *
 * <p>Reference-data lookups follow the same logic the FE pickers use: eligible supervisors are
 * active LABOR resources scoped to the project's pool (with a global fallback when the pool is
 * empty), activities are all rows for the project, BOQ items are the project's BOQ rows ordered
 * by item number.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DprVoiceFillService {

  private static final int MAX_REFERENCE_LIST_SIZE = 200;

  /**
   * Roles that back the DPR supervisor picker. Must mirror the frontend, which lists Users via
   * {@code GET /v1/users?roles=SUPERVISOR,FOREMAN,SITE_ENGINEER,SITE_MANAGER} and binds the User id
   * as {@code supervisorUserId}. Voice-fill resolves against the same User set (NOT LABOR Resources)
   * or it can never match the id the form expects.
   */
  private static final String SUPERVISOR_ROLES = "SUPERVISOR,FOREMAN,SITE_ENGINEER,SITE_MANAGER";

  private final SpeechToTextService speechToTextService;
  private final LlmProviderConfigRepository providerConfigRepository;
  private final OpenAiCompatibleProvider provider;
  private final DprVoiceFillSchema schemaBuilder;
  private final ObjectMapper objectMapper;

  private final UserService userService;
  private final ActivityRepository activityRepository;
  private final BoqItemRepository boqItemRepository;
  private final BoqService boqService;
  private final RoleRateService roleRateService;
  private final RoleAssignmentService roleAssignmentService;
  private final ModelCapabilityRegistry capabilityRegistry;

  public DprVoiceFillResponse fill(UUID projectId, MultipartFile audio, DprVoiceFillRequest request) {
    String filename = audio.getOriginalFilename() == null ? "audio.webm" : audio.getOriginalFilename();
    String mimeType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();

    byte[] bytes;
    try {
      bytes = audio.getBytes();
    } catch (IOException e) {
      throw new BusinessRuleException("VOICE_AUDIO_READ", "Failed to read audio payload");
    }

    // Reference data is loaded fresh per call (recently-pooled supervisors show immediately) and,
    // crucially, BEFORE transcription so we can bias Whisper toward this project's vocabulary.
    // Volumes are small (<200 in practice), so caching isn't justified yet.
    String activityIdStr = request.state().path("activityId").asText(null);
    UUID activityId = tryParseUuid(activityIdStr);
    ReferenceData refs = loadReferenceData(projectId, activityId);

    String transcript = speechToTextService.transcribe(bytes, filename, mimeType, transcriptionHint(refs));
    log.info("[dpr voice-fill] project={} transcript=\"{}\"", projectId,
        transcript.length() > 200 ? transcript.substring(0, 200) + "…" : transcript);

    return fillFromTranscript(projectId, transcript, refs, request);
  }

  /**
   * Typed-chat variant (client workbook, Web sheet row 9: "chat option to type the data").
   * Identical pipeline minus Whisper — the typed text plays the transcript's role, so the
   * response shape, session history and FE merge logic are unchanged.
   */
  public DprVoiceFillResponse fillFromText(UUID projectId, String text, DprVoiceFillRequest request) {
    String activityIdStr = request.state().path("activityId").asText(null);
    UUID activityId = tryParseUuid(activityIdStr);
    ReferenceData refs = loadReferenceData(projectId, activityId);

    log.info("[dpr voice-fill] project={} typed=\"{}\"", projectId,
        text.length() > 200 ? text.substring(0, 200) + "…" : text);

    return fillFromTranscript(projectId, text, refs, request);
  }

  private DprVoiceFillResponse fillFromTranscript(
      UUID projectId, String transcript, ReferenceData refs, DprVoiceFillRequest request) {
    LlmProviderConfig cfg = providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue()
        .orElseGet(() -> providerConfigRepository
            .findFirstByIsActiveTrueOrderByIsDefaultDescCreatedAtAsc()
            .orElseThrow(() -> new BusinessRuleException(
                "AI_NO_PROVIDER", "No active LLM provider configured")));

    LlmProvider.ChatRequest chatRequest = buildChatRequest(transcript, refs, request);
    LlmProvider.ChatResponse chatResponse;
    try {
      chatResponse = provider.chat(cfg, chatRequest);
    } catch (RuntimeException e) {
      log.error("[dpr voice-fill] LLM call failed", e);
      throw new BusinessRuleException("VOICE_LLM_FAILED", "AI form-fill failed: " + e.getMessage());
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(chatResponse.content());
    } catch (IOException e) {
      throw new BusinessRuleException("VOICE_LLM_BAD_JSON",
          "AI returned an unparseable response. Try again or fill the form manually.");
    }

    return validateAndAssemble(transcript, root, refs);
  }

  // ─── prompt assembly ──────────────────────────────────────────────────────────

  private LlmProvider.ChatRequest buildChatRequest(
      String transcript, ReferenceData refs, DprVoiceFillRequest req) {
    List<LlmProvider.Message> messages = new ArrayList<>();
    messages.add(new LlmProvider.Message("system", systemPrompt()));
    messages.add(new LlmProvider.Message("system", referenceDataPrompt(refs)));
    messages.add(new LlmProvider.Message("system", currentStatePrompt(req.state())));

    if (req.history() != null) {
      for (DprVoiceTurn turn : req.history()) {
        if (turn.role() == null || turn.content() == null) continue;
        messages.add(new LlmProvider.Message(turn.role(), turn.content()));
      }
    }
    messages.add(new LlmProvider.Message("user", transcript));

    JsonNode responseFormat = schemaBuilder.buildSchema();
    return new LlmProvider.ChatRequest(
        messages,
        null,        // no tools — pure structured output
        // Strict structured output forces EVERY schema field to be present, so a rich DPR (many
        // manpower/equipment/material rows) emits a large JSON document. 1024 truncated it
        // mid-object → unparseable JSON (VOICE_LLM_BAD_JSON). 4096 covers a full multi-row report.
        4096,        // maxTokens
        0.1,         // low temperature: factual extraction, not creative writing
        45_000L,     // 45 s timeout (the provider floors generation timeouts higher anyway)
        responseFormat);
  }

  private String systemPrompt() {
    return """
        You fill construction Daily Progress Reports (DPRs) for a road / corridor project. Be terse.
        Extract only what the user actually said. Do not invent supervisor / activity / BOQ-item /
        resource-assignment values that aren't in the provided lists.

        Rules:
        - Output strictly conforms to the response_format JSON schema. Set fields you didn't hear to null.
        - For EVERY dropdown field, ALWAYS emit the human label you heard — supervisorName, activityName,
          per-row trade / equipmentType / materialName, and unit — even when you are unsure of the id.
          The backend resolves ids from these labels, so a correct label matters more than the id.
        - Treat all id fields (supervisorUserId, activityId, boqItemId / boqItemNo, roleId, and the row
          variant ids) as best-effort: copy an exact id from the list when you are sure, otherwise leave
          the id null but STILL fill the label. Never invent an id.
        - For unit, output exactly one of the allowed unit codes (e.g. Cum, Sqm, MT, Nos) or null.
        - Default reportDate to today, shift to DAY, safetyIncidentType to NONE, approvalStatus to DRAFT.
        - Convert spoken chainages like "145 plus 200" or "145+200" into metres (145200).
        - When the user names a supervisor / activity / BOQ item ambiguously (no exact match, multiple
          near matches), set the related id to null and put the clarification in followUpQuestion.
        - When a quantity is given without a matching unit, ask for the unit in followUpQuestion.
        - Manpower / Equipment / Material rows are MERGED into the user's grid by trade/variant:
          a row matching one in CURRENT FORM STATE updates that row's numbers in place; a new
          trade is added. Emit ONLY rows the user mentioned this turn. When the user changes a
          value ("make masons 3", "excavator worked 6 hours"), re-emit that row with the new
          numbers — the form updates it, it will NOT duplicate.
        - To REMOVE a row the user asks to delete ("remove carpenter"), put its label into
          removeManpower / removeEquipment / removeMaterials. NEVER write edit commands into
          remarks — remarks is only for genuine site narrative the user dictates as a remark.
        - When the user asks to CHANGE an already-filled field (quantity, times, weather, ...),
          emit the new value for that field. Leave fields they didn't address at null.
        - resourceAssignmentId values must come from the provided assignment hints if any are listed;
          otherwise leave null and request clarification.
        - Set complete=true only when no follow-up is needed and you've captured every spoken fact.
        - assistantMessage is a short reply to the user (1-2 sentences) confirming what you heard
          plus the follow-up if any.
        - For manpower rows: set manpowerRoleRateId to the variantId from the Manpower roles
          list, roleId to the matching roleId, and trade to the role's roleName. When no exact
          match exists, set both ids to null and emit a follow-up question.
        - For equipment rows: set equipmentRoleVariantId to the variantId from the Equipment
          roles list, roleId to the matching roleId, and equipmentType to the role's roleName.
        - For material rows: set materialRoleVariantId to the variantId from the Material roles
          list, roleId to the matching roleId, and materialName to the role's roleName.
        """;
  }

  private String referenceDataPrompt(ReferenceData refs) {
    StringBuilder sb = new StringBuilder();
    sb.append("REFERENCE DATA for this project (use ONLY these for id resolution):\n\n");

    sb.append("Eligible supervisors (id — name [role]):\n");
    if (refs.supervisors().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (SupervisorRef s : refs.supervisors()) {
        sb.append("  ").append(s.id()).append(" — ").append(s.name());
        if (s.roleName() != null) sb.append(" [").append(s.roleName()).append("]");
        sb.append('\n');
      }
    }

    sb.append("\nProject activities (id — code — name):\n");
    if (refs.activities().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (ActivityRef a : refs.activities()) {
        sb.append("  ").append(a.id()).append(" — ")
            .append(a.code() == null ? "" : a.code()).append(" — ")
            .append(a.name()).append('\n');
      }
    }

    sb.append("\nBOQ items (itemNo — unit — description):\n");
    if (refs.boqItems().isEmpty()) {
      sb.append("  (none)\n");
    } else {
      for (BoqItemRef b : refs.boqItems()) {
        sb.append("  ").append(b.itemNo()).append(" — ")
            .append(b.unit() == null ? "" : b.unit()).append(" — ")
            .append(b.description()).append('\n');
      }
    }

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
    return sb.toString();
  }

  private String currentStatePrompt(JsonNode state) {
    return "CURRENT FORM STATE (emit only what the user addresses this turn: new fields, changed "
        + "values, rows to merge, or rows to remove — leave everything else null):\n"
        + state.toPrettyString();
  }

  /**
   * Domain vocabulary hint for Whisper: construction trades + this project's supervisor / activity /
   * role names, so accented dictation transcribes correctly (e.g. "masons" stays "masons", not
   * "machines"; "chainage" not "Chennai"). Whisper uses only the last ~224 tokens, so the most
   * project-specific names come last.
   */
  private String transcriptionHint(ReferenceData refs) {
    StringBuilder sb = new StringBuilder();
    sb.append("Daily Progress Report dictation for a road construction project. Terms: chainage, ")
      .append("manpower, mason, masons, helper, mazdoor, carpenter, fitter, welder, operator, ")
      .append("excavator, JCB, roller, grader, tipper, dozer, loader, crane, cement, steel, aggregate, ")
      .append("cubic meter, square meter, metric ton, running meter, workdone quantity, ")
      .append("day shift, night shift, LHS, RHS, BOQ, contractor. ");

    List<String> roles = new ArrayList<>();
    addNames(roles, refs.manpowerRoles().stream().map(ManpowerRoleRef::roleName).toList(), 20);
    addNames(roles, refs.equipmentRoles().stream().map(EquipmentRoleRef::roleName).toList(), 15);
    addNames(roles, refs.materialRoles().stream().map(MaterialRoleRef::roleName).toList(), 15);
    if (!roles.isEmpty()) sb.append("Resources: ").append(String.join(", ", roles)).append(". ");

    List<String> sups = new ArrayList<>();
    addNames(sups, refs.supervisors().stream().map(SupervisorRef::name).toList(), 20);
    if (!sups.isEmpty()) sb.append("Supervisors: ").append(String.join(", ", sups)).append(". ");

    List<String> acts = new ArrayList<>();
    addNames(acts, refs.activities().stream().map(ActivityRef::name).toList(), 12);
    if (!acts.isEmpty()) sb.append("Activities: ").append(String.join(", ", acts)).append(".");
    return sb.toString();
  }

  /** Append up to {@code cap} distinct, non-blank names to {@code out}. */
  private static void addNames(List<String> out, List<String> in, int cap) {
    int added = 0;
    for (String s : in) {
      if (s == null || s.isBlank() || out.contains(s)) continue;
      out.add(s);
      if (++added >= cap) break;
    }
  }

  // ─── validation + assembly ────────────────────────────────────────────────────

  private DprVoiceFillResponse validateAndAssemble(String transcript, JsonNode root, ReferenceData refs) {
    JsonNode patch = root.path("patch");
    String followUp = nullableText(root.path("followUpQuestion"));
    boolean complete = root.path("complete").asBoolean(false);
    String assistantMessage = root.path("assistantMessage").asText("OK.");

    List<String> demoted = new ArrayList<>();

    // Deterministic resolution: the LLM extracts spoken labels; we map each to the exact canonical
    // option the form's dropdown expects (a valid id / enum / unit code), or demote to a follow-up.
    if (patch instanceof ObjectNode obj) {
      resolveSupervisor(obj, refs, demoted);
      resolveActivity(obj, refs, demoted);
      resolveUnit(obj);
      resolveBoqItem(obj, refs, demoted);
      resolveRoleRows(obj, "manpower", refs.manpowerRoles(), "manpowerRoleRateId", "trade", "manpower role", demoted);
      resolveRoleRows(obj, "equipment", refs.equipmentRoles(), "equipmentRoleVariantId", "equipmentType", "equipment role", demoted);
      resolveRoleRows(obj, "materials", refs.materialRoles(), "materialRoleVariantId", "materialName", "material role", demoted);
    }

    if (!demoted.isEmpty() && (followUp == null || followUp.isBlank())) {
      followUp = "I couldn't match the " + String.join(" / ", new LinkedHashSet<>(demoted))
          + " against the project's list. Could you clarify?";
      complete = false;
    }

    DprVoiceTurn assistantTurn = new DprVoiceTurn("assistant", assistantMessage);
    List<DprVoiceFillResponse.PhotoCaption> photoCaptions = readPhotoCaptions(root.path("photoCaptions"));
    return new DprVoiceFillResponse(
        transcript, patch, photoCaptions, followUp, complete, assistantTurn);
  }

  // ─── field resolvers (spoken label → exact canonical option, else demote) ─────

  /** Supervisor: keep a valid User id; else resolve from the spoken name/code; else demote. */
  private void resolveSupervisor(ObjectNode obj, ReferenceData refs, List<String> demoted) {
    Set<UUID> valid = setOf(refs.supervisors(), s -> UUID.fromString(s.id()));
    UUID id = tryParseUuid(textOrNull(obj.path("supervisorUserId")));
    if (id != null && valid.contains(id)) {
      SupervisorRef ref = findById(refs.supervisors(), id.toString(), SupervisorRef::id);
      if (ref != null) obj.put("supervisorName", ref.name());
      return;
    }
    String name = textOrNull(obj.path("supervisorName"));
    DprLabelResolver.Resolved<SupervisorRef> r = DprLabelResolver.resolve(
        name, refs.supervisors(), s -> aliasList(s.name(), s.employeeCode(), s.username()));
    if (r.confident()) {
      obj.put("supervisorUserId", r.best().id());
      obj.put("supervisorName", r.best().name());
    } else {
      obj.putNull("supervisorUserId");
      if (name != null) demoted.add("supervisor");
    }
  }

  /** Activity: keep a valid activity id; else resolve from name/code; else demote. */
  private void resolveActivity(ObjectNode obj, ReferenceData refs, List<String> demoted) {
    Set<UUID> valid = setOf(refs.activities(), a -> UUID.fromString(a.id()));
    UUID id = tryParseUuid(textOrNull(obj.path("activityId")));
    if (id != null && valid.contains(id)) {
      ActivityRef ref = findById(refs.activities(), id.toString(), ActivityRef::id);
      if (ref != null) obj.put("activityName", ref.name());
      return;
    }
    String name = textOrNull(obj.path("activityName"));
    DprLabelResolver.Resolved<ActivityRef> r = DprLabelResolver.resolve(
        name, refs.activities(), a -> aliasList(a.name(), a.code()));
    if (r.confident()) {
      obj.put("activityId", r.best().id());
      obj.put("activityName", r.best().name());
    } else {
      obj.putNull("activityId");
      if (name != null) demoted.add("activity");
    }
  }

  /** Unit: normalize a spoken unit ("cubic meter") to a canonical STANDARD_UNITS code ("Cum"). */
  private void resolveUnit(ObjectNode obj) {
    String unit = textOrNull(obj.path("unit"));
    if (unit != null) obj.put("unit", DprUnits.canonical(unit));
  }

  /** BOQ: resolve itemNo / description → set boqItemNo (+ boqItemId when the candidate is known). */
  private void resolveBoqItem(ObjectNode obj, ReferenceData refs, List<String> demoted) {
    List<BoqItemRef> boqs = refs.boqItems();
    String itemNo = textOrNull(obj.path("boqItemNo"));
    if (itemNo == null) {
      obj.putNull("boqItemId");
      return;
    }
    BoqItemRef matched = null;
    for (BoqItemRef b : boqs) {
      if (itemNo.equals(b.itemNo())) { matched = b; break; }
    }
    if (matched == null) {
      DprLabelResolver.Resolved<BoqItemRef> r = DprLabelResolver.resolve(
          itemNo, boqs, b -> aliasList(b.itemNo(), b.description()));
      if (r.confident()) matched = r.best();
    }
    if (matched != null) {
      obj.put("boqItemNo", matched.itemNo());
      if (matched.id() != null) obj.put("boqItemId", matched.id());
      else obj.putNull("boqItemId");
    } else {
      obj.putNull("boqItemNo");
      obj.putNull("boqItemId");
      demoted.add("BOQ item");
    }
  }

  /**
   * Resolve appended grid rows to exact role FKs. A row already carrying a valid variant id is kept
   * but re-normalized (roleId + label from the matched ref) so the FE picker's composite key is
   * exact; otherwise the spoken label is resolved; unresolvable rows have their ids nulled (the FE
   * drops FK-less rows on submit) and add a follow-up. {@code unitRate} is intentionally left unset —
   * the DPR save path recomputes it from the variant FK.
   */
  private <T extends RoleRef> void resolveRoleRows(
      ObjectNode patch, String arrayKey, List<T> refs,
      String variantKey, String labelKey, String demoteLabel, List<String> demoted) {
    JsonNode arr = patch.path(arrayKey);
    if (!arr.isArray()) return;
    for (JsonNode row : arr) {
      if (!(row instanceof ObjectNode ro)) continue;
      String variantId = textOrNull(ro.path(variantKey));
      T ref = variantId == null ? null : findByVariant(refs, variantId);
      if (ref == null) {
        String label = textOrNull(ro.path(labelKey));
        DprLabelResolver.Resolved<T> r = DprLabelResolver.resolve(label, refs, x -> x.aliases());
        ref = r.confident() ? r.best() : null;
        if (ref == null) {
          ro.putNull(variantKey);
          ro.putNull("roleId");
          if (label != null) demoted.add(demoteLabel);
          continue;
        }
      }
      ro.put(variantKey, ref.variantId());
      ro.put("roleId", ref.roleId());
      ro.put(labelKey, ref.roleName());
    }
  }

  // ─── resolver helpers ─────────────────────────────────────────────────────────

  private static String textOrNull(JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull() || !n.isTextual()) return null;
    String s = n.asText();
    return s.isBlank() ? null : s;
  }

  private static List<String> aliasList(String... vals) {
    List<String> out = new ArrayList<>();
    for (String v : vals) if (v != null && !v.isBlank()) out.add(v);
    return out;
  }

  private static <T> T findById(List<T> items, String id, java.util.function.Function<T, String> idOf) {
    for (T t : items) if (id.equals(idOf.apply(t))) return t;
    return null;
  }

  private static <T extends RoleRef> T findByVariant(List<T> refs, String variantId) {
    for (T r : refs) if (variantId.equals(r.variantId())) return r;
    return null;
  }

  private List<DprVoiceFillResponse.PhotoCaption> readPhotoCaptions(JsonNode node) {
    if (!node.isArray()) return List.of();
    List<DprVoiceFillResponse.PhotoCaption> out = new ArrayList<>();
    for (JsonNode item : node) {
      String id = item.path("photoId").asText(null);
      String caption = item.path("caption").asText(null);
      if (id != null && !id.isBlank() && caption != null) {
        out.add(new DprVoiceFillResponse.PhotoCaption(id, caption));
      }
    }
    return out;
  }

  private static String nullableText(JsonNode n) {
    if (n.isMissingNode() || n.isNull()) return null;
    String s = n.asText("");
    return s.isBlank() ? null : s;
  }

  private static UUID tryParseUuid(String s) {
    if (s == null) return null;
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static <T> Set<UUID> setOf(List<T> items, java.util.function.Function<T, UUID> idOf) {
    Set<UUID> set = new HashSet<>();
    for (T t : items) {
      try {
        set.add(idOf.apply(t));
      } catch (RuntimeException ignored) {
        // skip malformed ids
      }
    }
    return set;
  }

  // ─── reference-data loading ──────────────────────────────────────────────────

  private ReferenceData loadReferenceData(UUID projectId, UUID activityId) {
    return new ReferenceData(
        loadSupervisors(),
        loadActivities(projectId),
        loadBoqItems(projectId, activityId),
        loadManpowerRoles(activityId),
        loadEquipmentRoles(activityId),
        loadMaterialRoles(activityId));
  }

  private List<SupervisorRef> loadSupervisors() {
    // Mirror the DPR supervisor picker: Users holding a supervisory role. The DPR's
    // supervisorUserId is a User id, so we resolve against Users — NOT LABOR Resources
    // (that list is empty on most projects and uses a different id space).
    List<SupervisorRef> out = new ArrayList<>();
    for (UserResponse u : userService.listUsers(
        PageRequest.of(0, MAX_REFERENCE_LIST_SIZE), SUPERVISOR_ROLES).getContent()) {
      String roleName = (u.roles() == null || u.roles().isEmpty()) ? null : u.roles().get(0);
      out.add(new SupervisorRef(u.id().toString(), displayName(u),
          u.employeeCode(), u.username(), roleName));
    }
    out.sort(Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT)));
    return cap(out);
  }

  private static String displayName(UserResponse u) {
    String first = u.firstName() == null ? "" : u.firstName().trim();
    String last = u.lastName() == null ? "" : u.lastName().trim();
    String full = (first + " " + last).trim();
    return full.isEmpty() ? u.username() : full;
  }

  private List<ActivityRef> loadActivities(UUID projectId) {
    List<Activity> activities = activityRepository.findByProjectId(projectId);
    List<ActivityRef> out = new ArrayList<>(activities.size());
    for (Activity a : activities) {
      out.add(new ActivityRef(a.getId().toString(), a.getCode(), a.getName()));
    }
    out.sort(Comparator.comparing(a -> a.name().toLowerCase(Locale.ROOT)));
    return cap(out);
  }

  private List<BoqItemRef> loadBoqItems(UUID projectId, UUID activityId) {
    // Mirror the FE BOQ picker: when an activity is chosen the picker runs in "candidate mode"
    // (BoqService.listForActivity → boqItemId), so resolving against that same candidate set means
    // the resolved boqItemId is guaranteed to be selectable. Fall back to the project-wide list
    // (fallback mode → boqItemNo) when there's no activity or no candidates.
    List<BoqItemRef> out = new ArrayList<>();
    if (activityId != null) {
      for (BoqItemResponse b : boqService.listForActivity(projectId, activityId)) {
        out.add(new BoqItemRef(b.id() == null ? null : b.id().toString(),
            b.itemNo(), b.description(), b.unit()));
      }
    }
    if (out.isEmpty()) {
      for (BoqItem b : boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId)) {
        out.add(new BoqItemRef(b.getId() == null ? null : b.getId().toString(),
            b.getItemNo(), b.getDescription(), b.getUnit()));
      }
    }
    return cap(out);
  }

  private List<ManpowerRoleRef> loadManpowerRoles(UUID activityId) {
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

  /** Cap reference lists to keep the prompt size predictable on huge projects. */
  private static <T> List<T> cap(List<T> in) {
    if (in.size() <= MAX_REFERENCE_LIST_SIZE) return in;
    return new ArrayList<>(in.subList(0, MAX_REFERENCE_LIST_SIZE));
  }

  // ─── records ─────────────────────────────────────────────────────────────────

  private record ReferenceData(
      List<SupervisorRef> supervisors,
      List<ActivityRef> activities,
      List<BoqItemRef> boqItems,
      List<ManpowerRoleRef> manpowerRoles,
      List<EquipmentRoleRef> equipmentRoles,
      List<MaterialRoleRef> materialRoles) {}

  private record SupervisorRef(String id, String name, String employeeCode, String username, String roleName) {}

  private record ActivityRef(String id, String code, String name) {}

  private record BoqItemRef(String id, String itemNo, String description, String unit) {}

  /** Common view over the three role reference kinds so one resolver can map a spoken label → FK. */
  private interface RoleRef {
    String variantId();
    String roleId();
    String roleName();
    /** Match aliases: the role name, plus qualified forms (grade / make-model / spec) for disambiguation. */
    List<String> aliases();
  }

  private record ManpowerRoleRef(
      String variantId, String roleId, String roleName,
      String categoryName, String gradeName, boolean planned) implements RoleRef {
    public List<String> aliases() {
      List<String> a = new ArrayList<>();
      a.add(roleName);
      if (gradeName != null && !gradeName.isBlank()) a.add(roleName + " " + gradeName);
      if (categoryName != null && !categoryName.isBlank()) a.add(roleName + " " + categoryName);
      return a;
    }
  }

  private record EquipmentRoleRef(
      String variantId, String roleId, String roleName,
      String make, String model, boolean planned) implements RoleRef {
    public List<String> aliases() {
      List<String> a = new ArrayList<>();
      a.add(roleName);
      if (make != null && !make.isBlank()) a.add(roleName + " " + make);
      if (model != null && !model.isBlank()) a.add(roleName + " " + model);
      return a;
    }
  }

  private record MaterialRoleRef(
      String variantId, String roleId, String roleName,
      String specGrade, String unit, boolean planned) implements RoleRef {
    public List<String> aliases() {
      List<String> a = new ArrayList<>();
      a.add(roleName);
      if (specGrade != null && !specGrade.isBlank()) a.add(roleName + " " + specGrade);
      return a;
    }
  }
}
