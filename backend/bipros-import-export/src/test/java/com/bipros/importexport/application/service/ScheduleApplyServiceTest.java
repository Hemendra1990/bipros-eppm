package com.bipros.importexport.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.importexport.application.dto.ApplySummary;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleApplyServiceTest {

  @Mock WbsNodeRepository wbsNodeRepository;
  @Mock ActivityRepository activityRepository;
  @Mock ActivityRelationshipRepository relationshipRepository;
  @Mock ResourceAssignmentRepository assignmentRepository;
  @Mock ResourceRepository resourceRepository;
  @Mock ResourceRoleRepository resourceRoleRepository;
  @Mock ResourceTypeRepository resourceTypeRepository;
  @Mock ProjectRepository projectRepository;

  ScheduleApplyService service;
  final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ScheduleApplyService(wbsNodeRepository, activityRepository,
        relationshipRepository, assignmentRepository, resourceRepository,
        resourceRoleRepository, resourceTypeRepository, projectRepository);
  }

  @Test
  void createsNewActivityWhenNoCodeMatch() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing",
            "target_start_date", "2026-01-01", "target_end_date", "2026-01-10", "wbs_id", "w1")));

    ApplySummary summary = service.apply(projectId, tables);

    assertEquals(1, summary.activitiesCreated());
    assertEquals(0, summary.activitiesUpdated());
    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertEquals("A1", cap.getValue().getCode());
    assertEquals(LocalDate.of(2026, 1, 1), cap.getValue().getPlannedStartDate());
    assertEquals(LocalDate.of(2026, 1, 10), cap.getValue().getPlannedFinishDate());
  }

  @Test
  void updatesPlannedDatesButPreservesActualsWhenCodeMatches() {
    Activity existing = new Activity();
    existing.setId(UUID.randomUUID());
    existing.setProjectId(projectId);
    existing.setCode("A1");
    existing.setName("Clearing");
    existing.setWbsNodeId(UUID.randomUUID());
    existing.setActualStartDate(LocalDate.of(2026, 1, 2));
    existing.setPercentComplete(0.4);

    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.of(existing));
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing",
            "target_start_date", "2026-02-01", "target_end_date", "2026-02-15", "wbs_id", "w1")));

    ApplySummary summary = service.apply(projectId, tables);

    assertEquals(0, summary.activitiesCreated());
    assertEquals(1, summary.activitiesUpdated());
    assertEquals(LocalDate.of(2026, 2, 1), existing.getPlannedStartDate());   // planned overwritten
    assertEquals(LocalDate.of(2026, 1, 2), existing.getActualStartDate());    // actual preserved
    assertEquals(0.4, existing.getPercentComplete());                          // progress preserved
  }

  @Test
  void createsRelationshipAndCostLoadedAssignment() {
    // one WBS + two activities so pred/succ resolve
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A2"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> { Activity a = inv.getArgument(0); if (a.getId()==null) a.setId("A1".equals(a.getCode())?a1:a2); return a; });
    when(relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(a1, a2)).thenReturn(false);
    when(resourceRepository.findByCode("R1")).thenReturn(Optional.empty());
    when(assignmentRepository.findByProjectIdAndActivityIdAndResourceId(eq(projectId), any(), any())).thenReturn(Optional.empty());
    when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // resource type/role seeding stubs — no RSRC table in this file, so rsrc_type is unknown and
    // ensureResource maps it to the MANPOWER default.
    var manpower = new com.bipros.resource.domain.model.ResourceType();
    when(resourceTypeRepository.findByCode("MANPOWER")).thenReturn(Optional.of(manpower));
    when(resourceRoleRepository.findByCode(any())).thenReturn(Optional.of(new com.bipros.resource.domain.model.ResourceRole()));
    when(resourceRepository.save(any())).thenAnswer(inv -> { var r = (com.bipros.resource.domain.model.Resource) inv.getArgument(0); r.setId(UUID.randomUUID()); return r; });

    Map<String, List<Map<String, String>>> tables = new java.util.HashMap<>();
    tables.put("PROJWBS", List.of(Map.of("wbs_id","w1","wbs_short_name","1.0","wbs_name","P")));
    tables.put("TASK", List.of(
        Map.of("task_code","A1","task_name","A one","wbs_id","w1"),
        Map.of("task_code","A2","task_name","A two","wbs_id","w1")));
    // non-default relationship type (PR_SS), proving the mapping isn't just always FS:
    tables.put("TASKPRED", List.of(Map.of("pred_task_id","A1","task_id","A2","pred_type","PR_SS","lag_hr_cnt","0")));
    tables.put("TASKRSRC", List.of(Map.of("task_id","A1","rsrc_id","R1","target_qty","10","target_cost","5000")));

    ApplySummary s = service.apply(projectId, tables);
    assertEquals(1, s.relationshipsCreated());
    assertEquals(1, s.assignmentsUpserted());

    ArgumentCaptor<ActivityRelationship> relCap = ArgumentCaptor.forClass(ActivityRelationship.class);
    verify(relationshipRepository).save(relCap.capture());
    assertEquals(RelationshipType.START_TO_START, relCap.getValue().getRelationshipType());

    ArgumentCaptor<ResourceAssignment> asgCap = ArgumentCaptor.forClass(ResourceAssignment.class);
    verify(assignmentRepository).save(asgCap.capture());
    assertEquals(0, new BigDecimal("5000").compareTo(asgCap.getValue().getPlannedCost()));
    assertEquals(10.0, asgCap.getValue().getPlannedUnits());
  }

  @Test
  void mapsXerRsrcTypeToAppResourceTypeUsingRsrcTableWithManpowerFallback() {
    // one activity, two resources: EXC (RT_Equip -> EQUIPMENT) and LAB (RT_Labor -> no LABOR type in this app -> MANPOWER)
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    UUID a1 = UUID.randomUUID();
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> { Activity a = inv.getArgument(0); a.setId(a1); return a; });
    when(assignmentRepository.findByProjectIdAndActivityIdAndResourceId(eq(projectId), any(), any())).thenReturn(Optional.empty());
    when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(resourceRepository.findByCode("EXC")).thenReturn(Optional.empty());
    when(resourceRepository.findByCode("LAB")).thenReturn(Optional.empty());
    var equipment = new com.bipros.resource.domain.model.ResourceType();
    var manpower = new com.bipros.resource.domain.model.ResourceType();
    when(resourceTypeRepository.findByCode("EQUIPMENT")).thenReturn(Optional.of(equipment));
    when(resourceTypeRepository.findByCode("MANPOWER")).thenReturn(Optional.of(manpower));
    when(resourceRoleRepository.findByCode(any())).thenReturn(Optional.of(new com.bipros.resource.domain.model.ResourceRole()));
    when(resourceRepository.save(any())).thenAnswer(inv -> { var r = (com.bipros.resource.domain.model.Resource) inv.getArgument(0); r.setId(UUID.randomUUID()); return r; });

    Map<String, List<Map<String, String>>> tables = new java.util.HashMap<>();
    tables.put("PROJWBS", List.of(Map.of("wbs_id","w1","wbs_short_name","1.0","wbs_name","P")));
    tables.put("TASK", List.of(Map.of("task_code","A1","task_name","A one","wbs_id","w1")));
    tables.put("RSRC", List.of(
        Map.of("rsrc_id","EXC","rsrc_type","RT_Equip"),
        Map.of("rsrc_id","LAB","rsrc_type","RT_Labor")));
    tables.put("TASKRSRC", List.of(
        Map.of("task_id","A1","rsrc_id","EXC","target_qty","5","target_cost","1000"),
        Map.of("task_id","A1","rsrc_id","LAB","target_qty","2","target_cost","200")));

    service.apply(projectId, tables);

    ArgumentCaptor<com.bipros.resource.domain.model.Resource> resCap =
        ArgumentCaptor.forClass(com.bipros.resource.domain.model.Resource.class);
    verify(resourceRepository, times(2)).save(resCap.capture());
    List<com.bipros.resource.domain.model.Resource> saved = resCap.getAllValues();
    com.bipros.resource.domain.model.Resource excResource =
        saved.stream().filter(r -> "EXC".equals(r.getCode())).findFirst().orElseThrow();
    com.bipros.resource.domain.model.Resource labResource =
        saved.stream().filter(r -> "LAB".equals(r.getCode())).findFirst().orElseThrow();
    assertEquals(equipment, excResource.getResourceType());
    assertEquals(manpower, labResource.getResourceType());
  }

  @Test
  void throwsBusinessRuleExceptionWhenManpowerResourceTypeNotSeeded() {
    // TASKRSRC row with no RSRC table -> rsrc_type unknown -> mapResourceType falls back to MANPOWER,
    // but the MANPOWER ResourceType row is missing from the DB (not seeded).
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    UUID a1 = UUID.randomUUID();
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> { Activity a = inv.getArgument(0); a.setId(a1); return a; });
    when(resourceRepository.findByCode("R1")).thenReturn(Optional.empty());
    when(resourceTypeRepository.findByCode("MANPOWER")).thenReturn(Optional.empty());

    Map<String, List<Map<String, String>>> tables = new java.util.HashMap<>();
    tables.put("PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "P")));
    tables.put("TASK", List.of(Map.of("task_code", "A1", "task_name", "A one", "wbs_id", "w1")));
    tables.put("TASKRSRC", List.of(Map.of("task_id", "A1", "rsrc_id", "R1", "target_qty", "10", "target_cost", "5000")));

    BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.apply(projectId, tables));
    assertEquals("MASTER_DATA_MISSING", ex.getRuleCode());
  }

  @Test
  void previewCountsMatchesAndWarnsOnZeroMatch() {
    Activity live = new Activity(); live.setCode("X9"); live.setProjectId(projectId);
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(live));
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id","w1","wbs_short_name","1.0")),
        "TASK", List.of(Map.of("task_code","A1","target_start_date","2026-01-01","target_end_date","2026-03-01")));

    var p = service.preview(projectId, tables);
    assertEquals(1, p.activitiesInFile());
    assertEquals(0, p.matched());
    assertEquals(1, p.newActivities());
    assertEquals(1, p.missingInFile());                 // X9 present live, not in file
    assertEquals(java.time.LocalDate.of(2026,1,1), p.dateRangeStart());
    assertTrue(p.warnings().stream().anyMatch(w -> w.contains("0 of 1")));
    verify(activityRepository, never()).save(any());    // dry-run
  }

  @Test
  void convertsDurationHoursToDaysUsingProjectCalendar() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    UUID calendarId = UUID.randomUUID();
    Project project = new Project();
    project.setCalendarId(calendarId);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(projectRepository.findCalendarHoursPerDay(calendarId)).thenReturn(Optional.of(9.0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing", "wbs_id", "w1",
            "target_drtn_hr_cnt", "90")));

    service.apply(projectId, tables);

    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertEquals(10.0, cap.getValue().getOriginalDuration());
  }

  @Test
  void convertsDurationHoursToDaysUsingDefaultEightHoursWhenNoCalendar() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(projectRepository.findById(projectId)).thenReturn(Optional.empty()); // no project found -> fallback

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing", "wbs_id", "w1",
            "target_drtn_hr_cnt", "80")));

    service.apply(projectId, tables);

    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertEquals(10.0, cap.getValue().getOriginalDuration());
  }

  @Test
  void mapsTtFinMileTaskTypeToFinishMilestone() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Milestone A", "wbs_id", "w1",
            "task_type", "TT_FinMile")));

    service.apply(projectId, tables);

    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertEquals(ActivityType.FINISH_MILESTONE, cap.getValue().getActivityType());
  }

  @Test
  void upsertWbsLinksChildToParentViaParentWbsId() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); if (w.getId() == null) w.setId(UUID.randomUUID()); return w; });

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(
            Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Parent"),
            Map.of("wbs_id", "w2", "wbs_short_name", "1.1", "wbs_name", "Child", "parent_wbs_id", "w1")),
        "TASK", List.of());

    service.apply(projectId, tables);

    ArgumentCaptor<WbsNode> cap = ArgumentCaptor.forClass(WbsNode.class);
    verify(wbsNodeRepository, atLeastOnce()).save(cap.capture());
    List<WbsNode> saved = cap.getAllValues();
    WbsNode parent = saved.stream().filter(n -> "1.0".equals(n.getCode())).findFirst().orElseThrow();
    WbsNode childWithParentSet = saved.stream()
        .filter(n -> "1.1".equals(n.getCode()) && n.getParentId() != null)
        .reduce((first, last) -> last)
        .orElseThrow(() -> new AssertionError("child WBS node was never saved with a parentId"));
    assertEquals(parent.getId(), childWithParentSet.getParentId());
  }

  @Test
  void createsRootWbsInsteadOfThrowingWhenActivityHasNoResolvableWbs() {
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });

    Map<String, List<Map<String, String>>> tables = Map.of(
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Orphan Task")));

    ApplySummary summary = assertDoesNotThrow(() -> service.apply(projectId, tables));

    assertEquals(1, summary.activitiesCreated());
    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertNotNull(cap.getValue().getWbsNodeId());

    ArgumentCaptor<WbsNode> wbsCap = ArgumentCaptor.forClass(WbsNode.class);
    verify(wbsNodeRepository).save(wbsCap.capture());
    assertEquals("ROOT", wbsCap.getValue().getCode());
  }

  @Test
  void populatesMissingActivityCodesInApplySummary() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    Activity live = new Activity(); live.setCode("X9"); live.setProjectId(projectId);
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(live));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing", "wbs_id", "w1")));

    ApplySummary summary = service.apply(projectId, tables);

    assertEquals(List.of("X9"), summary.missingActivityCodes());
  }

  @Test
  void setsPhysicalPercentCompleteAlongsidePercentCompleteForNewActivityOnly() {
    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.empty());
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing", "wbs_id", "w1",
            "phys_complete_pct", "40")));

    service.apply(projectId, tables);

    ArgumentCaptor<Activity> cap = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(cap.capture());
    assertEquals(0.4, cap.getValue().getPercentComplete());
    assertEquals(0.4, cap.getValue().getPhysicalPercentComplete());
  }

  @Test
  void doesNotSetPhysicalPercentCompleteForMatchedActivityEvenWhenFilePresent() {
    Activity existing = new Activity();
    existing.setId(UUID.randomUUID());
    existing.setProjectId(projectId);
    existing.setCode("A1");
    existing.setName("Clearing");
    existing.setWbsNodeId(UUID.randomUUID());
    existing.setPercentComplete(0.4);

    when(wbsNodeRepository.findByProjectIdAndCode(eq(projectId), any())).thenReturn(Optional.empty());
    when(wbsNodeRepository.save(any())).thenAnswer(inv -> { WbsNode w = inv.getArgument(0); w.setId(UUID.randomUUID()); return w; });
    when(activityRepository.findByProjectIdAndCode(eq(projectId), eq("A1"))).thenReturn(Optional.of(existing));
    when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, List<Map<String, String>>> tables = Map.of(
        "PROJWBS", List.of(Map.of("wbs_id", "w1", "wbs_short_name", "1.0", "wbs_name", "Prelims")),
        "TASK", List.of(Map.of("task_code", "A1", "task_name", "Clearing", "wbs_id", "w1",
            "phys_complete_pct", "90")));

    service.apply(projectId, tables);

    assertEquals(0.4, existing.getPercentComplete());
    assertNull(existing.getPhysicalPercentComplete());
  }
}
