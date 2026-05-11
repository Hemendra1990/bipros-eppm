package com.bipros.ai.query;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.query.SupervisorRosterCalculator.RankBy;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRoster;
import com.bipros.ai.query.SupervisorRosterCalculator.SupervisorRow;
import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.ProjectResource;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SupervisorRosterCalculator}. Each test mocks the JPA
 * repositories the calculator pulls from — the calculator does no SQL of its
 * own, so the mock surface is exactly the four repositories listed below
 * (Activity, ResourceAssignment, EvmCalculation, ProjectResource) plus the
 * Project and Resource lookups.
 */
@ExtendWith(MockitoExtension.class)
class SupervisorRosterCalculatorTest {

  @Mock private ActivityRepository activityRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceAssignmentRepository assignmentRepository;
  @Mock private EvmCalculationRepository evmRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private ProjectResourceRepository projectResourceRepository;

  private SupervisorRosterCalculator calculator;

  private final UUID projectId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    calculator = new SupervisorRosterCalculator(
        activityRepository,
        resourceRepository,
        assignmentRepository,
        evmRepository,
        projectRepository,
        projectResourceRepository);

    lenient().when(projectRepository.findById(projectId))
        .thenReturn(Optional.of(project("PRJ-1", "Demo Project")));
    // Default empty EVM lookup — individual tests override per-activity.
    lenient().when(evmRepository.findTopByProjectIdAndActivityIdOrderByDataDateDesc(
        eq(projectId), org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(Optional.empty());
  }

  @Test
  void threeSupervisorsAreReturnedSortedByActivityCountDesc() {
    UUID supA = UUID.randomUUID();
    UUID supB = UUID.randomUUID();
    UUID supC = UUID.randomUUID();

    // 3 acts for A, 2 for B, 1 for C
    Activity a1 = activity(supA, ActivityStatus.IN_PROGRESS, 50.0);
    Activity a2 = activity(supA, ActivityStatus.COMPLETED, 100.0);
    Activity a3 = activity(supA, ActivityStatus.NOT_STARTED, 0.0);
    Activity b1 = activity(supB, ActivityStatus.IN_PROGRESS, 25.0);
    Activity b2 = activity(supB, ActivityStatus.IN_PROGRESS, 75.0);
    Activity c1 = activity(supC, ActivityStatus.NOT_STARTED, 0.0);

    when(activityRepository.findByProjectId(projectId))
        .thenReturn(List.of(a1, a2, a3, b1, b2, c1));

    // One assignment per activity, varying planned/actual cost.
    ResourceAssignment ra1 = assignment(a1.getId(), "100", "120");
    ResourceAssignment ra2 = assignment(a2.getId(), "200", "180");
    ResourceAssignment rab1 = assignment(b1.getId(), "300", "350");
    when(assignmentRepository.findByActivityIdIn(anyList()))
        .thenReturn(List.of(ra1, ra2, rab1));

    when(resourceRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(
            resource(supA, "RES-A", "Alice", "Foreman"),
            resource(supB, "RES-B", "Bob", "Supervisor"),
            resource(supC, "RES-C", "Carol", "Lead Supervisor")));

    SupervisorRoster roster = calculator.compute(projectId, false, RankBy.ACTIVITY_COUNT, 50);

    assertThat(roster.projectCode()).isEqualTo("PRJ-1");
    assertThat(roster.projectName()).isEqualTo("Demo Project");
    assertThat(roster.totalSupervisors()).isEqualTo(3);
    assertThat(roster.rows()).hasSize(3);

    // Sort: A (3), B (2), C (1)
    assertThat(roster.rows().get(0).supervisorResourceId()).isEqualTo(supA);
    assertThat(roster.rows().get(0).activityCount()).isEqualTo(3);
    assertThat(roster.rows().get(0).statusBreakdown().notStarted()).isEqualTo(1);
    assertThat(roster.rows().get(0).statusBreakdown().inProgress()).isEqualTo(1);
    assertThat(roster.rows().get(0).statusBreakdown().completed()).isEqualTo(1);
    assertThat(roster.rows().get(0).statusBreakdown().onHold()).isZero();
    assertThat(roster.rows().get(0).avgPercentComplete()).isEqualByComparingTo("50.00");
    assertThat(roster.rows().get(0).plannedCost()).isEqualByComparingTo("300");
    assertThat(roster.rows().get(0).actualCost()).isEqualByComparingTo("300");
    assertThat(roster.rows().get(0).roleName()).isEqualTo("Foreman");
    assertThat(roster.rows().get(0).isInPool()).isFalse();

    assertThat(roster.rows().get(1).supervisorResourceId()).isEqualTo(supB);
    assertThat(roster.rows().get(1).activityCount()).isEqualTo(2);
    assertThat(roster.rows().get(1).plannedCost()).isEqualByComparingTo("300");
    assertThat(roster.rows().get(1).actualCost()).isEqualByComparingTo("350");

    assertThat(roster.rows().get(2).supervisorResourceId()).isEqualTo(supC);
    assertThat(roster.rows().get(2).activityCount()).isEqualTo(1);
    // C has no assignment rows → cost stays null
    assertThat(roster.rows().get(2).plannedCost()).isNull();
    assertThat(roster.rows().get(2).actualCost()).isNull();
  }

  @Test
  void includeEligiblePoolAppendsUnassignedLaborResources() {
    UUID supA = UUID.randomUUID();
    UUID poolOnly = UUID.randomUUID();
    UUID poolEquipment = UUID.randomUUID();

    Activity a1 = activity(supA, ActivityStatus.IN_PROGRESS, 40.0);
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(a1));
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of());
    when(resourceRepository.findAllById(eq(java.util.Set.of(supA))))
        .thenReturn(List.of(resource(supA, "RES-A", "Alice", "Foreman")));

    // Project pool: supA (already assigned, filtered out), poolOnly (LABOR, kept),
    // poolEquipment (EQUIPMENT, dropped).
    when(projectResourceRepository.findByProjectId(projectId)).thenReturn(List.of(
        projectResource(supA),
        projectResource(poolOnly),
        projectResource(poolEquipment)));

    when(resourceRepository.findAllById(org.mockito.ArgumentMatchers.argThat((Iterable<UUID> ids) -> {
      if (ids == null) return false;
      java.util.Set<UUID> s = new java.util.HashSet<>();
      ids.forEach(s::add);
      return s.size() == 2 && s.contains(poolOnly) && s.contains(poolEquipment);
    }))).thenReturn(List.of(
        resourceWithType(poolOnly, "RES-POOL", "Pavan", "Foreman", "LABOR"),
        resourceWithType(poolEquipment, "RES-EQ", "Bulldozer", "Operator", "EQUIPMENT")));

    SupervisorRoster roster = calculator.compute(projectId, true, RankBy.ACTIVITY_COUNT, 50);

    assertThat(roster.totalSupervisors()).isEqualTo(2);
    assertThat(roster.rows()).hasSize(2);

    SupervisorRow first = roster.rows().get(0);
    SupervisorRow second = roster.rows().get(1);
    // Assigned row first (activity_count 1 > 0); pool row second.
    assertThat(first.supervisorResourceId()).isEqualTo(supA);
    assertThat(first.isInPool()).isFalse();
    assertThat(first.activityCount()).isEqualTo(1);

    assertThat(second.supervisorResourceId()).isEqualTo(poolOnly);
    assertThat(second.isInPool()).isTrue();
    assertThat(second.activityCount()).isZero();
    assertThat(second.plannedCost()).isNull();
    assertThat(second.cpi()).isNull();
  }

  @Test
  void emptyProjectYieldsZeroRoster() {
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of());

    SupervisorRoster roster = calculator.compute(projectId, false, RankBy.ACTIVITY_COUNT, 50);

    assertThat(roster.totalSupervisors()).isZero();
    assertThat(roster.rows()).isEmpty();
    assertThat(roster.projectCode()).isEqualTo("PRJ-1");
  }

  @Test
  void rankByCpiSortsDescendingWithNullsLast() {
    UUID supA = UUID.randomUUID();
    UUID supB = UUID.randomUUID();
    UUID supC = UUID.randomUUID();

    Activity a1 = activity(supA, ActivityStatus.IN_PROGRESS, 50.0);
    Activity b1 = activity(supB, ActivityStatus.IN_PROGRESS, 50.0);
    Activity c1 = activity(supC, ActivityStatus.IN_PROGRESS, 50.0);

    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(a1, b1, c1));
    when(assignmentRepository.findByActivityIdIn(anyList())).thenReturn(List.of());
    when(resourceRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
        .thenReturn(List.of(
            resource(supA, "RES-A", "Alice", "Foreman"),
            resource(supB, "RES-B", "Bob", "Supervisor"),
            resource(supC, "RES-C", "Carol", "Lead")));

    // A: CPI 1.20, B: CPI 0.80, C: no EVM → null
    when(evmRepository.findTopByProjectIdAndActivityIdOrderByDataDateDesc(projectId, a1.getId()))
        .thenReturn(Optional.of(evm(1.20, 0.95)));
    when(evmRepository.findTopByProjectIdAndActivityIdOrderByDataDateDesc(projectId, b1.getId()))
        .thenReturn(Optional.of(evm(0.80, 0.70)));
    when(evmRepository.findTopByProjectIdAndActivityIdOrderByDataDateDesc(projectId, c1.getId()))
        .thenReturn(Optional.empty());

    SupervisorRoster roster = calculator.compute(projectId, false, RankBy.CPI, 50);

    assertThat(roster.rows()).hasSize(3);
    // A (1.20) → B (0.80) → C (null)
    assertThat(roster.rows().get(0).supervisorResourceId()).isEqualTo(supA);
    assertThat(roster.rows().get(0).cpi()).isEqualByComparingTo("1.2000");
    assertThat(roster.rows().get(1).supervisorResourceId()).isEqualTo(supB);
    assertThat(roster.rows().get(1).cpi()).isEqualByComparingTo("0.8000");
    assertThat(roster.rows().get(2).supervisorResourceId()).isEqualTo(supC);
    assertThat(roster.rows().get(2).cpi()).isNull();
  }

  // ---------- builders ----------

  private static Project project(String code, String name) {
    Project p = new Project();
    p.setId(UUID.randomUUID());
    p.setCode(code);
    p.setName(name);
    return p;
  }

  private static Activity activity(UUID supervisorId, ActivityStatus status, double percent) {
    Activity a = new Activity();
    a.setId(UUID.randomUUID());
    a.setResponsibleResourceId(supervisorId);
    a.setStatus(status);
    a.setPercentComplete(percent);
    return a;
  }

  private static ResourceAssignment assignment(UUID activityId, String planned, String actual) {
    ResourceAssignment ra = new ResourceAssignment();
    ra.setActivityId(activityId);
    ra.setPlannedCost(new BigDecimal(planned));
    ra.setActualCost(new BigDecimal(actual));
    return ra;
  }

  private static Resource resource(UUID id, String code, String name, String roleName) {
    return resourceWithType(id, code, name, roleName, "LABOR");
  }

  private static Resource resourceWithType(
      UUID id, String code, String name, String roleName, String typeCode) {
    Resource r = new Resource();
    r.setId(id);
    r.setCode(code);
    r.setName(name);
    ResourceRole role = new ResourceRole();
    role.setId(UUID.randomUUID());
    role.setCode(roleName.toUpperCase().replace(' ', '_'));
    role.setName(roleName);
    r.setRole(role);
    ResourceType type = new ResourceType();
    type.setId(UUID.randomUUID());
    type.setCode(typeCode);
    type.setName(typeCode);
    r.setResourceType(type);
    return r;
  }

  private static ProjectResource projectResource(UUID resourceId) {
    ProjectResource pr = new ProjectResource();
    pr.setId(UUID.randomUUID());
    pr.setResourceId(resourceId);
    return pr;
  }

  private static EvmCalculation evm(double cpi, double spi) {
    EvmCalculation e = new EvmCalculation();
    e.setCostPerformanceIndex(cpi);
    e.setSchedulePerformanceIndex(spi);
    return e;
  }
}
