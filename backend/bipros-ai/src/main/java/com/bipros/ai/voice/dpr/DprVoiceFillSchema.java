package com.bipros.ai.voice.dpr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the OpenAI-style {@code response_format = json_schema} payload that constrains the LLM
 * output to a {@link DprVoiceFillResponse}-shaped JSON document. We use {@code strict: true} so
 * the provider rejects any field the schema doesn't allow.
 *
 * <p>Field names mirror {@code DprBaseFields} on the FE so the FE can deep-merge the patch into
 * its form state without renaming. Row arrays carry the same per-row keys as the existing
 * {@code DprManpowerRow / DprEquipmentRow / DprMaterialRow} TypeScript types.
 */
@Component
public class DprVoiceFillSchema {

  private final ObjectMapper objectMapper;

  public DprVoiceFillSchema(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode buildSchema() {
    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("type", "json_schema");

    ObjectNode jsonSchema = objectMapper.createObjectNode();
    jsonSchema.put("name", "dpr_voice_fill_response");
    jsonSchema.put("strict", true);

    jsonSchema.set("schema", rootSchema());
    wrapper.set("json_schema", jsonSchema);
    return wrapper;
  }

  private ObjectNode rootSchema() {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);

    ObjectNode props = root.putObject("properties");
    props.set("patch", patchSchema());
    props.set("photoCaptions", photoCaptionsSchema());
    props.set("followUpQuestion", nullableString(
        "A clarifying question for the user when the patch is incomplete or ambiguous. "
            + "Null when no follow-up is needed."));
    props.set("complete", boolField(
        "True when no further input is required to finish the form."));
    props.set("assistantMessage", stringField(
        "A short conversational reply to the user (acknowledgement plus the follow-up if any)."));

    requireAll(root, List.of("patch", "photoCaptions", "followUpQuestion", "complete", "assistantMessage"));
    return root;
  }

  private ObjectNode patchSchema() {
    ObjectNode patch = objectMapper.createObjectNode();
    patch.put("type", "object");
    patch.put("additionalProperties", false);
    patch.put("description", "Partial DPR form state. Set only the fields the user mentioned; "
        + "every key is optional but must be present in this schema. Use null to mean 'unset'.");

    ObjectNode props = patch.putObject("properties");
    props.set("reportDate", nullableString("ISO date (yyyy-MM-dd). Default to today if not stated."));
    props.set("supervisorUserId", nullableString(
        "UUID of a supervisor from the provided eligible list. Pick by exact name match if unambiguous; "
            + "otherwise emit a follow-up question instead of guessing."));
    props.set("supervisorName", nullableString("Display name of the supervisor."));
    props.set("activityId", nullableString(
        "UUID of an activity from the provided activity list. Pick by exact name match if unambiguous; "
            + "otherwise emit a follow-up question."));
    props.set("activityName", nullableString("Display name of the activity."));
    props.set("contractorName", nullableString("Top-level contractor for the activity."));
    props.set("weatherCondition", nullableEnum(
        List.of("Clear", "Cloudy", "Rain", "Hot", "Cold", "Windy"),
        "Weather condition. Pick the closest match; null if not stated."));
    props.set("startTime", nullableString("Start time as HH:mm (24-hour)."));
    props.set("endTime", nullableString("End time as HH:mm (24-hour)."));
    props.set("shift", nullableEnum(List.of("DAY", "NIGHT"), "Shift. Default DAY when not stated."));
    props.set("approvalStatus", nullableEnum(
        List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED"),
        "Approval status. Voice-fill should normally leave this DRAFT."));
    props.set("side", nullableEnum(
        List.of("LHS", "RHS", "CENTER"),
        "Side of corridor. LHS=left, RHS=right, CENTER=median."));
    props.set("landmark", nullableString("Free-text landmark when chainage isn't given numerically."));
    props.set("chainageFromM", nullableInteger(
        "Chainage from, in metres. Convert km+metres (e.g. '145+200') to metres (145200)."));
    props.set("chainageToM", nullableInteger("Chainage to, in metres."));
    props.set("boqItemNo", nullableString("BOQ item number from the provided BOQ list."));
    props.set("boqItemId", nullableString(
        "UUID of the BOQ item when it matches the provided BOQ list; otherwise null."));
    props.set("unit", nullableEnum(DprUnits.STANDARD_UNITS,
        "Unit of measure for qtyExecuted. Use exactly one of these canonical codes, or null."));
    props.set("qtyExecuted", nullableNumber("Quantity executed today, in the matching unit."));
    props.set("remarks", nullableString("Free-text remarks. Capture context not covered by other fields."));
    props.set("delayReason", nullableString("Reason for any delay reported."));
    props.set("safetyObservation", nullableString("Safety observation reported by the supervisor."));
    props.set("safetyIncidentType", nullableEnum(
        List.of("NONE", "NEAR_MISS", "INCIDENT"),
        "Safety incident classification."));
    props.set("manpower", manpowerSchema());
    props.set("equipment", equipmentSchema());
    props.set("materials", materialsSchema());

    requireAll(patch, List.of(
        "reportDate", "supervisorUserId", "supervisorName", "activityId", "activityName",
        "contractorName", "weatherCondition", "startTime", "endTime", "shift", "approvalStatus",
        "side", "landmark", "chainageFromM", "chainageToM", "boqItemNo", "boqItemId", "unit", "qtyExecuted",
        "remarks", "delayReason", "safetyObservation", "safetyIncidentType",
        "manpower", "equipment", "materials"));
    return patch;
  }

  private ObjectNode manpowerSchema() {
    ObjectNode arr = objectMapper.createObjectNode();
    arr.put("type", "array");
    arr.put("description",
        "Rows to APPEND to the manpower grid. Do not include rows the user already entered. "
            + "Each row's resourceAssignmentId MUST come from the provided assignments list when "
            + "available — otherwise leave it null and emit a follow-up question.");

    ObjectNode item = arr.putObject("items");
    item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode props = item.putObject("properties");
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
    return arr;
  }

  private ObjectNode equipmentSchema() {
    ObjectNode arr = objectMapper.createObjectNode();
    arr.put("type", "array");
    arr.put("description", "Rows to APPEND to the equipment grid. Same picker semantics as manpower.");

    ObjectNode item = arr.putObject("items");
    item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode props = item.putObject("properties");
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
    return arr;
  }

  private ObjectNode materialsSchema() {
    ObjectNode arr = objectMapper.createObjectNode();
    arr.put("type", "array");
    arr.put("description", "Rows to APPEND to the materials grid. Same picker semantics as manpower.");

    ObjectNode item = arr.putObject("items");
    item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode props = item.putObject("properties");
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
    return arr;
  }

  private ObjectNode photoCaptionsSchema() {
    ObjectNode arr = objectMapper.createObjectNode();
    arr.put("type", "array");
    arr.put("description",
        "Caption updates for already-uploaded photos. Empty array when no photoIds were given.");

    ObjectNode item = arr.putObject("items");
    item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode props = item.putObject("properties");
    props.set("photoId", stringField("UUID of the photo to caption."));
    props.set("caption", stringField("New caption text."));
    requireAll(item, List.of("photoId", "caption"));
    return arr;
  }

  // ─── small helpers ────────────────────────────────────────────────────────────

  private ObjectNode stringField(String description) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("type", "string");
    n.put("description", description);
    return n;
  }

  private ObjectNode boolField(String description) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("type", "boolean");
    n.put("description", description);
    return n;
  }

  /** OpenAI strict mode requires nullables expressed as {@code type: ["string", "null"]}. */
  private ObjectNode nullableString(String description) {
    return nullableType("string", description);
  }

  private ObjectNode nullableInteger(String description) {
    return nullableType("integer", description);
  }

  private ObjectNode nullableNumber(String description) {
    return nullableType("number", description);
  }

  private ObjectNode nullableType(String typeName, String description) {
    ObjectNode n = objectMapper.createObjectNode();
    ArrayNode types = n.putArray("type");
    types.add(typeName);
    types.add("null");
    n.put("description", description);
    return n;
  }

  private ObjectNode nullableEnum(List<String> values, String description) {
    ObjectNode n = objectMapper.createObjectNode();
    ArrayNode types = n.putArray("type");
    types.add("string");
    types.add("null");
    ArrayNode enumValues = n.putArray("enum");
    for (String v : values) enumValues.add(v);
    enumValues.add((String) null); // strict-mode-compatible null
    n.put("description", description);
    return n;
  }

  private void requireAll(ObjectNode parent, List<String> names) {
    ArrayNode required = parent.putArray("required");
    for (String n : names) required.add(n);
  }
}
