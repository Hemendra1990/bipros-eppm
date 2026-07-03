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
import com.bipros.resource.domain.model.ResourceType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleResourcePlanApplierTest {

  @Mock ActivityRepository activityRepository;
  @Mock ResourceRoleRepository roleRepository;
  @Mock ManpowerCategoryMasterRepository categoryRepository;
  @Mock GradeMasterRepository gradeRepository;
  @Mock ManpowerRoleRateRepository manpowerRoleRateRepository;
  @Mock EquipmentRoleVariantRepository equipmentRoleVariantRepository;
  @Mock MaterialRoleVariantRepository materialRoleVariantRepository;
  @Mock SubContractorMasterRepository subContractorMasterRepository;
  @Mock SubContractorWorkTypeRepository subContractorWorkTypeRepository;
  @Mock ResourceAssignmentRepository resourceAssignmentRepository;
  @Mock ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;
  @Mock RoleAssignmentService roleAssignmentService;
  @Mock ActivitySubContractorAssignmentService activitySubContractorAssignmentService;
  @Mock RoleRateResolver roleRateResolver;
  @Mock SubContractorWorkActivityMappingRepository subContractorWorkActivityMappingRepository;
  @Mock WorkActivityRepository workActivityRepository;

  RoleResourcePlanApplier applier;
  final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    applier = new RoleResourcePlanApplier(
        activityRepository,
        roleRepository,
        categoryRepository,
        gradeRepository,
        manpowerRoleRateRepository,
        equipmentRoleVariantRepository,
        materialRoleVariantRepository,
        subContractorMasterRepository,
        subContractorWorkTypeRepository,
        resourceAssignmentRepository,
        activitySubContractorAssignmentRepository,
        roleAssignmentService,
        activitySubContractorAssignmentService,
        roleRateResolver,
        subContractorWorkActivityMappingRepository,
        workActivityRepository);
  }

  private static Activity activity(UUID id, String code) {
    Activity a = new Activity();
    a.setId(id);
    a.setCode(code);
    return a;
  }

  private static ResourceRole role(UUID id, String code, String typeCode) {
    ResourceRole r = new ResourceRole();
    r.setId(id);
    r.setCode(code);
    ResourceType type = new ResourceType();
    type.setCode(typeCode);
    r.setResourceType(type);
    return r;
  }

  @Test
  void manpowerRow_resolvesAndCreatesRoleAssignment() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID gradeId = UUID.randomUUID();
    UUID mrrId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CARPENTER"))
        .thenReturn(Optional.of(role(roleId, "CARPENTER", "MANPOWER")));
    ManpowerCategoryMaster cat = ManpowerCategoryMaster.builder().build();
    cat.setId(categoryId);
    when(categoryRepository.findByName("Skilled")).thenReturn(Optional.of(cat));
    GradeMaster grade = GradeMaster.builder().build();
    grade.setId(gradeId);
    when(gradeRepository.findByName("Grade A")).thenReturn(Optional.of(grade));
    ManpowerRoleRate mrr = ManpowerRoleRate.builder().build();
    mrr.setId(mrrId);
    when(manpowerRoleRateRepository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
        .thenReturn(Optional.of(mrr));
    when(roleRateResolver.resolveRate(projectId, "MANPOWER", mrrId)).thenReturn(BigDecimal.TEN);
    when(resourceAssignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
    verify(roleAssignmentService).createRoleAssignment(eq(projectId), captor.capture());
    RoleAssignmentRequest req = captor.getValue();
    assertEquals(activityId, req.activityId());
    assertEquals(roleId, req.roleId());
    assertEquals(mrrId, req.manpowerRoleRateId());
    assertEquals(5, req.headcount());

    assertEquals(1, result.manpowerRows());
    assertEquals(1, result.manpowerApplied());
    assertTrue(result.warnings().isEmpty());
  }

  @Test
  void manpowerRow_unknownRoleCode_warnsAndDoesNotThrow() {
    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(UUID.randomUUID(), "A1")));
    when(roleRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "UNKNOWN",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    assertEquals(1, result.manpowerRows());
    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("role")));
  }

  @Test
  void equipmentRow_resolvesAndCreatesRoleAssignment() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID variantId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("EXCAVATOR"))
        .thenReturn(Optional.of(role(roleId, "EXCAVATOR", "EQUIPMENT")));
    EquipmentRoleVariant variant = EquipmentRoleVariant.builder().build();
    variant.setId(variantId);
    when(equipmentRoleVariantRepository.findByRoleIdAndMakeAndModel(roleId, "GENERIC", "STD"))
        .thenReturn(Optional.of(variant));
    when(roleRateResolver.resolveRate(projectId, "EQUIPMENT", variantId)).thenReturn(BigDecimal.TEN);
    when(resourceAssignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "EQUIPMENT", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "EXCAVATOR",
            "make", "GENERIC",
            "model", "STD",
            "nos", "3")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
    verify(roleAssignmentService).createRoleAssignment(eq(projectId), captor.capture());
    assertEquals(variantId, captor.getValue().equipmentRoleVariantId());
    assertEquals(3, captor.getValue().headcount());
    assertEquals(1, result.equipmentApplied());
  }

  @Test
  void materialRow_resolvesAndCreatesRoleAssignment() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID variantId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CONCRETE"))
        .thenReturn(Optional.of(role(roleId, "CONCRETE", "MATERIAL")));
    MaterialRoleVariant variant = MaterialRoleVariant.builder().build();
    variant.setId(variantId);
    when(materialRoleVariantRepository.findByRoleIdAndSpecGrade(roleId, "C30"))
        .thenReturn(Optional.of(variant));
    when(roleRateResolver.resolveRate(projectId, "MATERIAL", variantId)).thenReturn(BigDecimal.TEN);
    when(resourceAssignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MATERIAL", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CONCRETE",
            "spec_grade", "C30",
            "quantity", "50")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    ArgumentCaptor<RoleAssignmentRequest> captor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
    verify(roleAssignmentService).createRoleAssignment(eq(projectId), captor.capture());
    assertEquals(variantId, captor.getValue().materialRoleVariantId());
    assertEquals(0, new BigDecimal("50").compareTo(captor.getValue().quantity()));
    assertEquals(1, result.materialApplied());
  }

  @Test
  void subContractorRow_resolvesAndCreatesAssignment() {
    UUID activityId = UUID.randomUUID();
    UUID masterId = UUID.randomUUID();
    UUID workTypeId = UUID.randomUUID();
    UUID workActivityId = UUID.randomUUID();

    Activity activity = activity(activityId, "A1");
    activity.setWorkActivityId(workActivityId);
    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity));
    SubContractorMaster master = SubContractorMaster.builder().build();
    master.setId(masterId);
    when(subContractorMasterRepository.findByCode("SC-01")).thenReturn(Optional.of(master));
    SubContractorWorkType workType = SubContractorWorkType.builder().build();
    workType.setId(workTypeId);
    when(subContractorWorkTypeRepository.findByNameIgnoreCase("Asphalt Laying"))
        .thenReturn(Optional.of(workType));
    WorkActivity workActivity = WorkActivity.builder().defaultUnit("SQM").build();
    when(workActivityRepository.findById(workActivityId)).thenReturn(Optional.of(workActivity));
    SubContractorWorkActivityMapping mapping =
        SubContractorWorkActivityMapping.builder().unit("sqm").ratePerUnit(BigDecimal.TEN).build();
    when(subContractorWorkActivityMappingRepository.findBySubContractorMasterIdAndScWorkTypeId(
            masterId, workTypeId))
        .thenReturn(Optional.of(mapping));
    when(resourceAssignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "SUBCONTRACTOR", List.of(Map.of(
            "activity_code", "A1",
            "sub_contractor_code", "SC-01",
            "work_type", "Asphalt Laying",
            "quantity", "500")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    ArgumentCaptor<CreateActivitySubContractorAssignmentRequest> captor =
        ArgumentCaptor.forClass(CreateActivitySubContractorAssignmentRequest.class);
    verify(activitySubContractorAssignmentService).create(eq(projectId), captor.capture());
    assertEquals(activityId.toString(), captor.getValue().activityId());
    assertEquals(masterId.toString(), captor.getValue().subContractorMasterId());
    assertEquals(workTypeId.toString(), captor.getValue().scWorkTypeId());
    assertEquals(1, result.subContractorApplied());
  }

  @Test
  void createRoleAssignment_throwsBusinessRuleException_warnsAndDoesNotThrow() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID gradeId = UUID.randomUUID();
    UUID mrrId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CARPENTER"))
        .thenReturn(Optional.of(role(roleId, "CARPENTER", "MANPOWER")));
    ManpowerCategoryMaster cat = ManpowerCategoryMaster.builder().build();
    cat.setId(categoryId);
    when(categoryRepository.findByName("Skilled")).thenReturn(Optional.of(cat));
    GradeMaster grade = GradeMaster.builder().build();
    grade.setId(gradeId);
    when(gradeRepository.findByName("Grade A")).thenReturn(Optional.of(grade));
    ManpowerRoleRate mrr = ManpowerRoleRate.builder().build();
    mrr.setId(mrrId);
    when(manpowerRoleRateRepository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
        .thenReturn(Optional.of(mrr));
    when(roleRateResolver.resolveRate(projectId, "MANPOWER", mrrId)).thenReturn(BigDecimal.TEN);
    when(resourceAssignmentRepository.findByActivityId(activityId)).thenReturn(List.of());
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of());
    when(roleAssignmentService.createRoleAssignment(eq(projectId), any()))
        .thenThrow(new BusinessRuleException("RATE_NOT_FOUND", "No rate available"));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.contains("RATE_NOT_FOUND") || w.toLowerCase().contains("rate")));
  }

  @Test
  void firstTouchOfActivity_clearsPlannedRowsWithoutActuals_preservesRowsWithActuals() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID gradeId = UUID.randomUUID();
    UUID mrrId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CARPENTER"))
        .thenReturn(Optional.of(role(roleId, "CARPENTER", "MANPOWER")));
    ManpowerCategoryMaster cat = ManpowerCategoryMaster.builder().build();
    cat.setId(categoryId);
    when(categoryRepository.findByName("Skilled")).thenReturn(Optional.of(cat));
    GradeMaster grade = GradeMaster.builder().build();
    grade.setId(gradeId);
    when(gradeRepository.findByName("Grade A")).thenReturn(Optional.of(grade));
    ManpowerRoleRate mrr = ManpowerRoleRate.builder().build();
    mrr.setId(mrrId);
    when(manpowerRoleRateRepository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
        .thenReturn(Optional.of(mrr));
    when(roleRateResolver.resolveRate(projectId, "MANPOWER", mrrId)).thenReturn(BigDecimal.TEN);

    ResourceAssignment noActuals = ResourceAssignment.builder().build();
    noActuals.setId(UUID.randomUUID());
    noActuals.setActualUnits(null);
    noActuals.setActualCost(null);

    ResourceAssignment withActuals = ResourceAssignment.builder().build();
    withActuals.setId(UUID.randomUUID());
    withActuals.setActualUnits(2.0);
    withActuals.setActualCost(BigDecimal.TEN);

    when(resourceAssignmentRepository.findByActivityId(activityId))
        .thenReturn(List.of(noActuals, withActuals));

    ActivitySubContractorAssignment scNoActuals = ActivitySubContractorAssignment.builder().build();
    scNoActuals.setId(UUID.randomUUID());
    scNoActuals.setActualUnits(BigDecimal.ZERO);
    scNoActuals.setActualCost(BigDecimal.ZERO);
    when(activitySubContractorAssignmentRepository.findByProjectIdAndActivityId(projectId, activityId))
        .thenReturn(List.of(scNoActuals));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    applier.apply(projectId, tables);

    verify(resourceAssignmentRepository).delete(noActuals);
    verify(resourceAssignmentRepository, never()).delete(withActuals);
    verify(activitySubContractorAssignmentRepository).delete(scNoActuals);
  }

  @Test
  void preview_resolvesAndCounts_butDoesNotCreateOrDelete() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID gradeId = UUID.randomUUID();
    UUID mrrId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CARPENTER"))
        .thenReturn(Optional.of(role(roleId, "CARPENTER", "MANPOWER")));
    ManpowerCategoryMaster cat = ManpowerCategoryMaster.builder().build();
    cat.setId(categoryId);
    when(categoryRepository.findByName("Skilled")).thenReturn(Optional.of(cat));
    GradeMaster grade = GradeMaster.builder().build();
    grade.setId(gradeId);
    when(gradeRepository.findByName("Grade A")).thenReturn(Optional.of(grade));
    ManpowerRoleRate mrr = ManpowerRoleRate.builder().build();
    mrr.setId(mrrId);
    when(manpowerRoleRateRepository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
        .thenReturn(Optional.of(mrr));
    when(roleRateResolver.resolveRate(projectId, "MANPOWER", mrrId)).thenReturn(BigDecimal.TEN);

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.preview(projectId, tables);

    assertEquals(1, result.manpowerRows());
    assertEquals(1, result.manpowerApplied());
    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    verify(resourceAssignmentRepository, never()).delete(any());
    verify(resourceAssignmentRepository, never()).findByActivityId(any());
  }

  @Test
  void manpowerRow_rateResolverReturnsNull_warnsAndSkipsWithoutCallingService() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID gradeId = UUID.randomUUID();
    UUID mrrId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("CARPENTER"))
        .thenReturn(Optional.of(role(roleId, "CARPENTER", "MANPOWER")));
    ManpowerCategoryMaster cat = ManpowerCategoryMaster.builder().build();
    cat.setId(categoryId);
    when(categoryRepository.findByName("Skilled")).thenReturn(Optional.of(cat));
    GradeMaster grade = GradeMaster.builder().build();
    grade.setId(gradeId);
    when(gradeRepository.findByName("Grade A")).thenReturn(Optional.of(grade));
    ManpowerRoleRate mrr = ManpowerRoleRate.builder().build();
    mrr.setId(mrrId);
    when(manpowerRoleRateRepository.findByRoleIdAndCategoryIdAndGradeId(roleId, categoryId, gradeId))
        .thenReturn(Optional.of(mrr));
    when(roleRateResolver.resolveRate(projectId, "MANPOWER", mrrId)).thenReturn(null);

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("rate")));
  }

  @Test
  void manpowerRow_lockedActivity_skipsWithoutClearingOrCreating() {
    UUID activityId = UUID.randomUUID();
    Activity locked = activity(activityId, "A1");
    locked.setEditStatus(ActivityEditStatus.LOCKED);
    when(activityRepository.findByProjectIdAndCode(projectId, "A1")).thenReturn(Optional.of(locked));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(resourceAssignmentRepository, never()).findByActivityId(any());
    verify(resourceAssignmentRepository, never()).delete(any());
    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("locked")));
  }

  @Test
  void manpowerRow_roleResourceTypeNull_warnsAndSkipsWithoutCallingService() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    ResourceRole roleWithNullType = new ResourceRole();
    roleWithNullType.setId(roleId);
    roleWithNullType.setCode("CARPENTER");
    when(roleRepository.findByCode("CARPENTER")).thenReturn(Optional.of(roleWithNullType));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "CARPENTER",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("resource type")));
  }

  @Test
  void manpowerRow_roleTypeMismatch_warnsAndSkipsWithoutCallingService() {
    UUID activityId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    when(roleRepository.findByCode("EXCAVATOR"))
        .thenReturn(Optional.of(role(roleId, "EXCAVATOR", "EQUIPMENT")));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "MANPOWER", List.of(Map.of(
            "activity_code", "A1",
            "role_code", "EXCAVATOR",
            "category", "Skilled",
            "grade", "Grade A",
            "nos", "5")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(roleAssignmentService, never()).createRoleAssignment(any(), any());
    assertEquals(0, result.manpowerApplied());
    assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase().contains("not a manpower role")));
  }

  @Test
  void subContractorRow_mappingMissing_warnsAndSkipsWithoutCallingService() {
    UUID activityId = UUID.randomUUID();
    UUID masterId = UUID.randomUUID();
    UUID workTypeId = UUID.randomUUID();

    when(activityRepository.findByProjectIdAndCode(projectId, "A1"))
        .thenReturn(Optional.of(activity(activityId, "A1")));
    SubContractorMaster master = SubContractorMaster.builder().build();
    master.setId(masterId);
    when(subContractorMasterRepository.findByCode("SC-01")).thenReturn(Optional.of(master));
    SubContractorWorkType workType = SubContractorWorkType.builder().build();
    workType.setId(workTypeId);
    when(subContractorWorkTypeRepository.findByNameIgnoreCase("Asphalt Laying"))
        .thenReturn(Optional.of(workType));
    when(subContractorWorkActivityMappingRepository.findBySubContractorMasterIdAndScWorkTypeId(
            masterId, workTypeId))
        .thenReturn(Optional.empty());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "SUBCONTRACTOR", List.of(Map.of(
            "activity_code", "A1",
            "sub_contractor_code", "SC-01",
            "work_type", "Asphalt Laying",
            "quantity", "500")));

    ResourceApplyResult result = applier.apply(projectId, tables);

    verify(activitySubContractorAssignmentService, never()).create(any(), any());
    assertEquals(0, result.subContractorApplied());
    assertTrue(
        result.warnings().stream()
            .anyMatch(w -> w.toLowerCase().contains("mapping") || w.toLowerCase().contains("work-type")));
  }
}
