package com.bipros.ai.tool.resource;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.query.ResourceContextFacade;
import com.bipros.ai.query.ResourceProfile;
import com.bipros.ai.resolver.EffectiveRate;
import com.bipros.ai.resolver.EffectiveRateResolver;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single-call resource profile. Returns Resource + ResourceRole + ResourceType +
 * ManpowerMaster + ManpowerSkills + ResourceRate (with budgeted-vs-actual
 * variance) + parent + subordinates.
 *
 * <p>Identify the resource by ID, code, or employee_code. The {@code include}
 * array controls which sub-blocks to return (default = everything); pass a
 * narrower set to keep the response compact when you only need rates or skills.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetResourceProfileTool implements Tool {

  private final ResourceContextFacade facade;
  private final EffectiveRateResolver rateResolver;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "get_resource_profile";
  }

  @Override
  public String description() {
    return "Full profile for one resource — role, type, hierarchy parent + subordinates, "
        + "manpower master (designation, employment type, dates), skills (primary, secondary, "
        + "certifications, licenses), and rate history (budgeted vs actual with variance). "
        + "Identify by resource_id (UUID), resource_code (e.g. RES-MASON-001), or "
        + "employee_code (the employee_code from manpower_master). Use the include array to "
        + "narrow the response to specific blocks. Use this for: \"What skills does Resource X "
        + "have?\", \"Show me Foreman John's profile\", \"What's the budgeted vs actual rate "
        + "for the crane operator?\". Also returns effective_rate (project pool override → "
        + "resource base), unit_basis, rate_source, and override_applied so the AI can disclose "
        + "project-specific rate overrides when a project is in scope.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    props.set(
        "resource_id",
        objectMapper.createObjectNode().put("type", "string").put("format", "uuid"));
    props.set(
        "resource_code",
        objectMapper.createObjectNode().put("type", "string").put("description", "Resource code (unique)."));
    props.set(
        "employee_code",
        objectMapper.createObjectNode().put("type", "string").put("description", "ManpowerMaster employee code."));
    ArrayNode includeEnum = objectMapper.createArrayNode();
    includeEnum.add("rates");
    includeEnum.add("hierarchy");
    includeEnum.add("manpower");
    includeEnum.add("skills");
    ObjectNode incNode = objectMapper.createObjectNode();
    incNode.put("type", "array");
    incNode.set("items", objectMapper.createObjectNode().put("type", "string").set("enum", includeEnum));
    incNode.put(
        "description",
        "Sub-blocks to include. Default: all. Pass [\"manpower\", \"skills\"] for HR-style "
            + "questions, [\"rates\"] for cost questions, [\"hierarchy\"] for org-chart questions.");
    props.set("include", incNode);
    schema.set("properties", props);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID id = resolveId(input);
    if (id == null) {
      return ToolResult.error(
          "Provide one of resource_id, resource_code, or employee_code. Use resolve_entity for fuzzy matching.");
    }

    EnumSet<ResourceContextFacade.Include> include = parseInclude(input);
    Optional<ResourceProfile> opt = facade.loadProfile(id, include);
    if (opt.isEmpty()) return ToolResult.error("No resource with that identifier.");
    ResourceProfile p = opt.get();

    ObjectNode wrapper = objectMapper.createObjectNode();
    wrapper.put("resource_id", p.resourceId().toString());
    wrapper.put("code", p.code());
    wrapper.put("name", p.name());
    wrapper.put("description", p.description());
    wrapper.put("unit", p.unit());
    wrapper.put("status", p.status());
    wrapper.put("availability", p.availability() == null ? null : p.availability().doubleValue());
    wrapper.put("cost_per_unit", p.costPerUnit() == null ? null : p.costPerUnit().doubleValue());
    wrapper.put("role_id", p.roleId() == null ? null : p.roleId().toString());
    wrapper.put("role_code", p.roleCode());
    wrapper.put("role_name", p.roleName());
    wrapper.put("resource_type_id", p.resourceTypeId() == null ? null : p.resourceTypeId().toString());
    wrapper.put("resource_type_code", p.resourceTypeCode());
    wrapper.put("resource_type_name", p.resourceTypeName());
    wrapper.put("resource_type_category", p.resourceTypeCategory());

    // Effective rate (project pool override → resource base). When no project is
    // in scope, the resolver returns the base rate with override_applied=false.
    EffectiveRate er = rateResolver.resolve(ctx.projectId(), p.resourceId());
    wrapper.put("effective_rate", er.rate() == null ? null : er.rate().doubleValue());
    wrapper.put("effective_unit", er.unit());
    wrapper.put("unit_basis", er.basis());
    wrapper.put("rate_source", er.source().name());
    wrapper.put("override_applied", er.overrideApplied());
    wrapper.put("base_cost_per_unit", p.costPerUnit() == null ? null : p.costPerUnit().doubleValue());
    ArrayNode rateNotes = objectMapper.createArrayNode();
    if (er.overrideApplied()) {
      rateNotes.add("rate_overridden_per_project");
    } else if (ctx.projectId() == null) {
      rateNotes.add("profile_view_no_project_override_applied");
    }
    wrapper.set("formula_overrides", rateNotes);

    if (p.parentId() != null) {
      ObjectNode parentNode = objectMapper.createObjectNode();
      parentNode.put("resource_id", p.parentId().toString());
      parentNode.put("code", p.parentCode());
      parentNode.put("name", p.parentName());
      wrapper.set("parent", parentNode);
    }

    if (p.manpower() != null) {
      ResourceProfile.Manpower m = p.manpower();
      ObjectNode mp = objectMapper.createObjectNode();
      mp.put("employee_code", m.employeeCode());
      mp.put("full_name", m.fullName());
      mp.put("designation", m.designation());
      mp.put("department", m.department());
      mp.put("category", m.category());
      mp.put("sub_category", m.subCategory());
      mp.put("employment_type", m.employmentType());
      mp.put("nationality", m.nationality());
      mp.put("contact_number", m.contactNumber());
      mp.put("email", m.email());
      mp.put("joining_date", m.joiningDate() == null ? null : m.joiningDate().toString());
      mp.put("exit_date", m.exitDate() == null ? null : m.exitDate().toString());
      mp.put("reporting_manager_id", m.reportingManagerId() == null ? null : m.reportingManagerId().toString());
      mp.put("reporting_manager_name", m.reportingManagerName());
      mp.put("company_name", m.companyName());
      mp.put("work_location", m.workLocation());
      wrapper.set("manpower", mp);
    }

    if (p.skills() != null) {
      ResourceProfile.Skills s = p.skills();
      ObjectNode sk = objectMapper.createObjectNode();
      sk.put("primary_skill", s.primarySkill());
      sk.put("secondary_skills_json", s.secondarySkillsJson());
      sk.put("skill_level", s.skillLevel());
      sk.put("certifications_json", s.certificationsJson());
      sk.put("license_details_json", s.licenseDetailsJson());
      sk.put("training_records_json", s.trainingRecordsJson());
      sk.put("experience_years", s.experienceYears());
      wrapper.set("skills", sk);
    }

    if (!p.rates().isEmpty()) {
      ArrayNode rateRows = objectMapper.createArrayNode();
      for (ResourceProfile.RateSnapshot r : p.rates()) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("rate_type", r.rateType());
        n.put("price_per_unit", r.pricePerUnit() == null ? null : r.pricePerUnit().doubleValue());
        n.put("budgeted_rate", r.budgetedRate() == null ? null : r.budgetedRate().doubleValue());
        n.put("actual_rate", r.actualRate() == null ? null : r.actualRate().doubleValue());
        n.put("variance", r.variance() == null ? null : r.variance().doubleValue());
        Double pct =
            r.variance() != null && r.budgetedRate() != null && r.budgetedRate().compareTo(BigDecimal.ZERO) != 0
                ? r.variance().doubleValue() / r.budgetedRate().doubleValue() * 100.0
                : null;
        n.put("variance_pct", pct);
        n.put("effective_date", r.effectiveDate() == null ? null : r.effectiveDate().toString());
        n.put("effective_to", r.effectiveTo() == null ? null : r.effectiveTo().toString());
        n.put("category", r.category());
        rateRows.add(n);
      }
      wrapper.set("rates", rateRows);
    }

    if (!p.subordinates().isEmpty()) {
      ArrayNode subRows = objectMapper.createArrayNode();
      for (ResourceProfile.Subordinate s : p.subordinates()) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("resource_id", s.resourceId().toString());
        n.put("code", s.code());
        n.put("name", s.name());
        n.put("full_name", s.fullName());
        n.put("designation", s.designation());
        n.put("role", s.roleName());
        n.put("type_category", s.resourceTypeCategory());
        n.put("link_source", s.linkSource());
        subRows.add(n);
      }
      wrapper.set("subordinates", subRows);
    }

    Map<String, List<UUID>> links = new HashMap<>();
    Set<UUID> subordinateIds = new HashSet<>();
    for (ResourceProfile.Subordinate s : p.subordinates()) subordinateIds.add(s.resourceId());
    if (!subordinateIds.isEmpty()) links.put("subordinates", List.copyOf(subordinateIds));
    if (p.parentId() != null) links.put("parent", List.of(p.parentId()));
    ToolResult.attachLinks(wrapper, links);

    String label = p.name() != null ? p.name() : p.code();
    if (p.manpower() != null && p.manpower().fullName() != null) label = p.manpower().fullName();
    String summary =
        label
            + " ("
            + (p.code() != null ? p.code() : p.resourceId())
            + ") — "
            + (p.roleName() != null ? p.roleName() : "?")
            + (p.resourceTypeCategory() != null ? " · " + p.resourceTypeCategory() : "")
            + (p.subordinates().isEmpty() ? "" : ", " + p.subordinates().size() + " on team")
            + (p.rates().isEmpty() ? "" : ", " + p.rates().size() + " rate" + (p.rates().size() == 1 ? "" : "s"));
    return ToolResult.ok(summary, wrapper);
  }

  private UUID resolveId(JsonNode input) {
    String idStr = orNull(input.path("resource_id").asText(null));
    if (idStr != null) {
      try {
        return UUID.fromString(idStr);
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    String code = orNull(input.path("resource_code").asText(null));
    if (code != null) {
      Optional<UUID> id = facade.resolveResourceId(code);
      if (id.isPresent()) return id.get();
    }
    String emp = orNull(input.path("employee_code").asText(null));
    if (emp != null) {
      Optional<UUID> id = facade.resolveResourceId(emp);
      if (id.isPresent()) return id.get();
    }
    return null;
  }

  private EnumSet<ResourceContextFacade.Include> parseInclude(JsonNode input) {
    JsonNode arr = input.path("include");
    if (!arr.isArray() || arr.isEmpty()) return EnumSet.allOf(ResourceContextFacade.Include.class);
    EnumSet<ResourceContextFacade.Include> out = EnumSet.noneOf(ResourceContextFacade.Include.class);
    for (JsonNode n : arr) {
      String v = n.asText(null);
      if (v == null) continue;
      try {
        out.add(ResourceContextFacade.Include.valueOf(v.trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // skip unknown
      }
    }
    return out.isEmpty() ? EnumSet.allOf(ResourceContextFacade.Include.class) : out;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
