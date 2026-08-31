package com.bipros.importexport.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.importexport.application.dto.ResourceApplyResult;
import com.bipros.resource.application.dto.CreateActivitySubContractorAssignmentRequest;
import com.bipros.resource.application.dto.role.RoleAssignmentRequest;
import com.bipros.resource.application.service.ActivitySubContractorAssignmentService;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateResolver;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.SubContractorWorkActivityMapping;
import com.bipros.resource.domain.model.SubContractorWorkType;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.master.SubContractorMaster;
import com.bipros.resource.domain.model.role.EquipmentRoleVariant;
import com.bipros.resource.domain.model.role.ManpowerRoleRate;
import com.bipros.resource.domain.model.role.MaterialRoleVariant;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.SubContractorMasterRepository;
import com.bipros.resource.domain.repository.SubContractorWorkActivityMappingRepository;
import com.bipros.resource.domain.repository.SubContractorWorkTypeRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import com.bipros.resource.domain.repository.role.EquipmentRoleVariantRepository;
import com.bipros.resource.domain.repository.role.ManpowerRoleRateRepository;
import com.bipros.resource.domain.repository.role.MaterialRoleVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves Task 1's canonical resource tables (MANPOWER/EQUIPMENT/MATERIAL/SUBCONTRACTOR) onto
 * this app's role+variant model and applies them by calling the SAME services the UI's "Add"
 * button uses — {@link RoleAssignmentService#createRoleAssignment} and
 * {@link ActivitySubContractorAssignmentService#create} — so rate/cost computation is never
 * duplicated here.
 *
 * <p>Unresolved rows (unknown activity/role/category/grade/variant/sub-contractor, a locked
 * activity, a missing rate, or a missing/mismatched sub-contractor work-type mapping) add a
 * warning and are skipped — they never fail the import. These conditions are pre-validated
 * BEFORE calling the services below: {@code apply()} runs in one transaction shared by the
 * services it calls (both {@code @Transactional(REQUIRED)}), so a downstream
 * {@link BusinessRuleException} — even one caught here — marks the whole transaction
 * rollback-only and fails the entire import at commit. The {@code try/catch} around each
 * service call remains only as a backstop for anything pre-validation doesn't anticipate.
 *
 * <p>Replace semantics: the first time an activity is touched by {@link #apply}, its existing
 * planned rows (ResourceAssignment / ActivitySubContractorAssignment) that carry no logged
 * actuals are deleted before the file's rows are (re)created; rows with actuals are preserved.
 */
@Service
@RequiredArgsConstructor
public class RoleResourcePlanApplier {

  private final ActivityRepository activityRepository;
  private final ResourceRoleRepository roleRepository;
  private final ManpowerCategoryMasterRepository categoryRepository;
  private final GradeMasterRepository gradeRepository;
  private final ManpowerRoleRateRepository manpowerRoleRateRepository;
  private final EquipmentRoleVariantRepository equipmentRoleVariantRepository;
  private final MaterialRoleVariantRepository materialRoleVariantRepository;
  private final SubContractorMasterRepository subContractorMasterRepository;
  private final SubContractorWorkTypeRepository subContractorWorkTypeRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;
  private final RoleAssignmentService roleAssignmentService;
  private final ActivitySubContractorAssignmentService activitySubContractorAssignmentService;
  private final RoleRateResolver roleRateResolver;
  private final SubContractorWorkActivityMappingRepository subContractorWorkActivityMappingRepository;
  private final WorkActivityRepository workActivityRepository;

  @Transactional
  public ResourceApplyResult apply(UUID projectId, Map<String, List<Map<String, String>>> tables) {
    return run(projectId, tables, true);
  }

  @Transactional(readOnly = true)
  public ResourceApplyResult preview(UUID projectId, Map<String, List<Map<String, String>>> tables) {
    return run(projectId, tables, false);
  }

  private ResourceApplyResult run(
      UUID projectId, Map<String, List<Map<String, String>>> tables, boolean doApply) {
    List<String> warnings = new ArrayList<>();
    Set<UUID> touchedActivities = new HashSet<>();

    List<Map<String, String>> manpowerRows = tables.getOrDefault("MANPOWER", List.of());
    int manpowerApplied = 0;
    for (Map<String, String> row : manpowerRows) {
      RoleAssignmentRequest req = resolveManpower(projectId, row, warnings);
      if (req == null) continue;
      if (doApply) {
        ensureActivityCleared(projectId, req.activityId(), touchedActivities);
        if (!tryCreateRoleAssignment(projectId, req, warnings)) continue;
      }
      manpowerApplied++;
    }

    List<Map<String, String>> equipmentRows = tables.getOrDefault("EQUIPMENT", List.of());
    int equipmentApplied = 0;
    for (Map<String, String> row : equipmentRows) {
      RoleAssignmentRequest req = resolveEquipment(projectId, row, warnings);
      if (req == null) continue;
      if (doApply) {
        ensureActivityCleared(projectId, req.activityId(), touchedActivities);
        if (!tryCreateRoleAssignment(projectId, req, warnings)) continue;
      }
      equipmentApplied++;
    }

    List<Map<String, String>> materialRows = tables.getOrDefault("MATERIAL", List.of());
    int materialApplied = 0;
    for (Map<String, String> row : materialRows) {
      RoleAssignmentRequest req = resolveMaterial(projectId, row, warnings);
      if (req == null) continue;
      if (doApply) {
        ensureActivityCleared(projectId, req.activityId(), touchedActivities);
        if (!tryCreateRoleAssignment(projectId, req, warnings)) continue;
      }
      materialApplied++;
    }

    List<Map<String, String>> subContractorRows = tables.getOrDefault("SUBCONTRACTOR", List.of());
    int subContractorApplied = 0;
    for (Map<String, String> row : subContractorRows) {
      ResolvedSubContractor resolved = resolveSubContractor(projectId, row, warnings);
      if (resolved == null) continue;
      if (doApply) {
        ensureActivityCleared(projectId, resolved.activityId(), touchedActivities);
        if (!tryCreateSubContractorAssignment(projectId, resolved.request(), warnings)) continue;
      }
      subContractorApplied++;
    }

    return new ResourceApplyResult(
        manpowerRows.size(), manpowerApplied,
        equipmentRows.size(), equipmentApplied,
        materialRows.size(), materialApplied,
        subContractorRows.size(), subContractorApplied,
        warnings);
  }

  // ===== Resolution (activity/role/variant lookups — shared by apply() and preview()) =====

  private RoleAssignmentRequest resolveManpower(
      UUID projectId, Map<String, String> row, List<String> warnings) {
    String activityCode = row.get("activity_code");
    String roleCode = row.get("role_code");
    String category = row.get("category");
    String grade = row.get("grade");

    Activity activity = activityRepository.findByProjectIdAndCode(projectId, activityCode).orElse(null);
    if (activity == null) {
      warnings.add("MANPOWER row skipped: activity not found for code '" + activityCode + "'");
      return null;
    }
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      warnings.add("MANPOWER row skipped (activity " + activityCode + "): activity is locked");
      return null;
    }
    ResourceRole role = roleRepository.findByCode(roleCode).orElse(null);
    if (role == null) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + "): role not found for code '"
              + roleCode + "'");
      return null;
    }
    if (role.getResourceType() == null) {
      warnings.add("Role " + roleCode + ": no resource type configured — skipped.");
      return null;
    }
    String roleTypeCode = role.getResourceType().getCode().toUpperCase();
    if (!roleTypeCode.equals("MANPOWER") && !roleTypeCode.equals("LABOR")) {
      warnings.add("Manpower row: role " + roleCode + " is not a manpower role");
      return null;
    }
    ManpowerCategoryMaster cat =
        categoryRepository.findByName(category).or(() -> categoryRepository.findByCode(category)).orElse(null);
    if (cat == null) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + ", role " + roleCode
              + "): category not found '" + category + "'");
      return null;
    }
    GradeMaster gradeMaster =
        gradeRepository.findByCode(grade).or(() -> gradeRepository.findByName(grade)).orElse(null);
    if (gradeMaster == null) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + ", role " + roleCode
              + "): grade not found '" + grade + "'");
      return null;
    }
    ManpowerRoleRate rate =
        manpowerRoleRateRepository
            .findByRoleIdAndCategoryIdAndGradeId(role.getId(), cat.getId(), gradeMaster.getId())
            .orElse(null);
    if (rate == null) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + ", role " + roleCode
              + "): no rate for category '" + category + "' / grade '" + grade + "'");
      return null;
    }
    if (roleRateResolver.resolveRate(projectId, "MANPOWER", rate.getId()) == null) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + ", role " + roleCode
              + "): no rate available for category '" + category + "' / grade '" + grade + "'");
      return null;
    }
    Integer headcount = parseInt(row.get("nos"));
    if (headcount == null || headcount <= 0) {
      warnings.add(
          "MANPOWER row skipped (activity " + activityCode + ", role " + roleCode
              + "): invalid nos '" + row.get("nos") + "'");
      return null;
    }
    return new RoleAssignmentRequest(
        activity.getId(), role.getId(), rate.getId(), null, null, headcount, null, null, null, null, null);
  }

  private RoleAssignmentRequest resolveEquipment(
      UUID projectId, Map<String, String> row, List<String> warnings) {
    String activityCode = row.get("activity_code");
    String roleCode = row.get("role_code");
    String make = row.get("make");
    String model = row.get("model");

    Activity activity = activityRepository.findByProjectIdAndCode(projectId, activityCode).orElse(null);
    if (activity == null) {
      warnings.add("EQUIPMENT row skipped: activity not found for code '" + activityCode + "'");
      return null;
    }
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      warnings.add("EQUIPMENT row skipped (activity " + activityCode + "): activity is locked");
      return null;
    }
    ResourceRole role = roleRepository.findByCode(roleCode).orElse(null);
    if (role == null) {
      warnings.add(
          "EQUIPMENT row skipped (activity " + activityCode + "): role not found for code '"
              + roleCode + "'");
      return null;
    }
    if (role.getResourceType() == null) {
      warnings.add("Role " + roleCode + ": no resource type configured — skipped.");
      return null;
    }
    String roleTypeCode = role.getResourceType().getCode().toUpperCase();
    if (!roleTypeCode.equals("EQUIPMENT")) {
      warnings.add("Equipment row: role " + roleCode + " is not an equipment role");
      return null;
    }
    EquipmentRoleVariant variant =
        equipmentRoleVariantRepository.findByRoleIdAndMakeAndModel(role.getId(), make, model).orElse(null);
    if (variant == null) {
      warnings.add(
          "EQUIPMENT row skipped (activity " + activityCode + ", role " + roleCode
              + "): no variant for make '" + make + "' / model '" + model + "'");
      return null;
    }
    if (roleRateResolver.resolveRate(projectId, "EQUIPMENT", variant.getId()) == null) {
      warnings.add(
          "EQUIPMENT row skipped (activity " + activityCode + ", role " + roleCode
              + "): no rate available for make '" + make + "' / model '" + model + "'");
      return null;
    }
    Integer headcount = parseInt(row.get("nos"));
    if (headcount == null || headcount <= 0) {
      warnings.add(
          "EQUIPMENT row skipped (activity " + activityCode + ", role " + roleCode
              + "): invalid nos '" + row.get("nos") + "'");
      return null;
    }
    return new RoleAssignmentRequest(
        activity.getId(), role.getId(), null, variant.getId(), null, headcount, null, null, null, null, null);
  }

  private RoleAssignmentRequest resolveMaterial(
      UUID projectId, Map<String, String> row, List<String> warnings) {
    String activityCode = row.get("activity_code");
    String roleCode = row.get("role_code");
    String specGrade = row.get("spec_grade");

    Activity activity = activityRepository.findByProjectIdAndCode(projectId, activityCode).orElse(null);
    if (activity == null) {
      warnings.add("MATERIAL row skipped: activity not found for code '" + activityCode + "'");
      return null;
    }
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      warnings.add("MATERIAL row skipped (activity " + activityCode + "): activity is locked");
      return null;
    }
    ResourceRole role = roleRepository.findByCode(roleCode).orElse(null);
    if (role == null) {
      warnings.add(
          "MATERIAL row skipped (activity " + activityCode + "): role not found for code '"
              + roleCode + "'");
      return null;
    }
    if (role.getResourceType() == null) {
      warnings.add("Role " + roleCode + ": no resource type configured — skipped.");
      return null;
    }
    String roleTypeCode = role.getResourceType().getCode().toUpperCase();
    if (!roleTypeCode.equals("MATERIAL")) {
      warnings.add("Material row: role " + roleCode + " is not a material role");
      return null;
    }
    MaterialRoleVariant variant =
        materialRoleVariantRepository.findByRoleIdAndSpecGrade(role.getId(), specGrade).orElse(null);
    if (variant == null) {
      warnings.add(
          "MATERIAL row skipped (activity " + activityCode + ", role " + roleCode
              + "): no variant for spec/grade '" + specGrade + "'");
      return null;
    }
    if (roleRateResolver.resolveRate(projectId, "MATERIAL", variant.getId()) == null) {
      warnings.add(
          "MATERIAL row skipped (activity " + activityCode + ", role " + roleCode
              + "): no rate available for spec/grade '" + specGrade + "'");
      return null;
    }
    BigDecimal quantity = parseBigDecimal(row.get("quantity"));
    if (quantity == null || quantity.signum() <= 0) {
      warnings.add(
          "MATERIAL row skipped (activity " + activityCode + ", role " + roleCode
              + "): invalid quantity '" + row.get("quantity") + "'");
      return null;
    }
    return new RoleAssignmentRequest(
        activity.getId(), role.getId(), null, null, variant.getId(), null, null, quantity, null, null, null);
  }

  private record ResolvedSubContractor(
      UUID activityId, CreateActivitySubContractorAssignmentRequest request) {}

  private ResolvedSubContractor resolveSubContractor(
      UUID projectId, Map<String, String> row, List<String> warnings) {
    String activityCode = row.get("activity_code");
    String subContractorCode = row.get("sub_contractor_code");
    String workType = row.get("work_type");

    Activity activity = activityRepository.findByProjectIdAndCode(projectId, activityCode).orElse(null);
    if (activity == null) {
      warnings.add("SUBCONTRACTOR row skipped: activity not found for code '" + activityCode + "'");
      return null;
    }
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      warnings.add("SUBCONTRACTOR row skipped (activity " + activityCode + "): activity is locked");
      return null;
    }
    SubContractorMaster master = subContractorMasterRepository.findByCode(subContractorCode).orElse(null);
    if (master == null) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode
              + "): sub-contractor not found for code '" + subContractorCode + "'");
      return null;
    }
    SubContractorWorkType scWorkType =
        subContractorWorkTypeRepository.findByNameIgnoreCase(workType).orElse(null);
    if (scWorkType == null) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode + ", sub-contractor "
              + subContractorCode + "): work type not found '" + workType + "'");
      return null;
    }
    SubContractorWorkActivityMapping mapping =
        subContractorWorkActivityMappingRepository
            .findBySubContractorMasterIdAndScWorkTypeId(master.getId(), scWorkType.getId())
            .orElse(null);
    if (mapping == null) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode + ", sub-contractor "
              + subContractorCode + "): no work-type mapping for '" + workType + "'");
      return null;
    }
    String activityUnit = resolveActivityUnit(activity);
    if (activityUnit == null) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode + ", sub-contractor "
              + subContractorCode + "): activity's work-activity unit could not be resolved");
      return null;
    }
    if (mapping.getUnit() == null || !activityUnit.equalsIgnoreCase(mapping.getUnit())) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode + ", sub-contractor "
              + subContractorCode + "): mapping unit '" + mapping.getUnit()
              + "' does not match activity unit '" + activityUnit + "'");
      return null;
    }
    BigDecimal quantity = parseBigDecimal(row.get("quantity"));
    if (quantity == null || quantity.signum() <= 0) {
      warnings.add(
          "SUBCONTRACTOR row skipped (activity " + activityCode + ", sub-contractor "
              + subContractorCode + "): invalid quantity '" + row.get("quantity") + "'");
      return null;
    }
    CreateActivitySubContractorAssignmentRequest req =
        new CreateActivitySubContractorAssignmentRequest(
            activity.getId().toString(), master.getId().toString(), scWorkType.getId().toString(), quantity);
    return new ResolvedSubContractor(activity.getId(), req);
  }

  /**
   * Resolves the activity's workdone unit via {@code Activity.workActivityId →
   * WorkActivity.defaultUnit} — mirrors {@code ActivitySubContractorAssignmentService}'s own
   * resolution so the pre-check here agrees with what the service would compute. Returns null
   * when the activity has no work-activity link or the link doesn't resolve.
   */
  private String resolveActivityUnit(Activity activity) {
    if (activity.getWorkActivityId() == null) return null;
    WorkActivity wa = workActivityRepository.findById(activity.getWorkActivityId()).orElse(null);
    return wa == null ? null : wa.getDefaultUnit();
  }

  // ===== Apply-only: create via the real services, clear-before-recreate =====

  private boolean tryCreateRoleAssignment(
      UUID projectId, RoleAssignmentRequest req, List<String> warnings) {
    try {
      roleAssignmentService.createRoleAssignment(projectId, req);
      return true;
    } catch (BusinessRuleException e) {
      warnings.add(
          "Resource row skipped (activity " + req.activityId() + ", role " + req.roleId()
              + "): " + e.getRuleCode() + " — " + e.getMessage());
      return false;
    }
  }

  private boolean tryCreateSubContractorAssignment(
      UUID projectId, CreateActivitySubContractorAssignmentRequest req, List<String> warnings) {
    try {
      activitySubContractorAssignmentService.create(projectId, req);
      return true;
    } catch (BusinessRuleException e) {
      warnings.add(
          "Sub-contractor row skipped (activity " + req.activityId() + "): "
              + e.getRuleCode() + " — " + e.getMessage());
      return false;
    }
  }

  /**
   * First touch of an activity in this run deletes its existing planned rows that carry no
   * logged actuals, so the file's rows fully replace the prior plan. Rows with actuals (DPR
   * already booked against them) are preserved and skipped. No-op on later touches of the same
   * activity in the same run.
   */
  private void ensureActivityCleared(UUID projectId, UUID activityId, Set<UUID> touchedActivities) {
    if (!touchedActivities.add(activityId)) {
      return;
    }
    for (ResourceAssignment ra : resourceAssignmentRepository.findByActivityId(activityId)) {
      if (hasNoActuals(ra)) {
        resourceAssignmentRepository.delete(ra);
      }
    }
    for (ActivitySubContractorAssignment sc :
        activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId)) {
      if (hasNoActuals(sc)) {
        activitySubContractorAssignmentRepository.delete(sc);
      }
    }
  }

  private static boolean hasNoActuals(ResourceAssignment ra) {
    boolean noUnits = ra.getActualUnits() == null || ra.getActualUnits() == 0.0;
    boolean noCost = ra.getActualCost() == null || ra.getActualCost().signum() == 0;
    return noUnits && noCost;
  }

  private static boolean hasNoActuals(ActivitySubContractorAssignment sc) {
    boolean noUnits = sc.getActualUnits() == null || sc.getActualUnits().signum() == 0;
    boolean noCost = sc.getActualCost() == null || sc.getActualCost().signum() == 0;
    return noUnits && noCost;
  }

  private static Integer parseInt(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return (int) Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal parseBigDecimal(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
