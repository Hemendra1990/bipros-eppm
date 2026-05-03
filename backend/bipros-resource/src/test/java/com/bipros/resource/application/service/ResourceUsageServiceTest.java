package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.calendar.application.service.CalendarService;
import com.bipros.calendar.application.service.CalendarSnapshot;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ActivityUsage;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ResourceTypeUsage;
import com.bipros.resource.application.dto.ResourceUsageTimePhasedResponse.ResourceUsage;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceUsageService.getTimePhased")
class ResourceUsageServiceTest {

  @Mock private ResourceAssignmentRepository assignmentRepository;
  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceRoleRepository roleRepository;
  @Mock private ResourceTypeRepository resourceTypeRepository;
  @Mock private ActivityRepository activityRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private DailyActivityResourceOutputRepository dailyOutputRepository;
  @Mock private CalendarService calendarService;

  @InjectMocks private ResourceUsageService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID calendarId = UUID.randomUUID();

  private ResourceType labourType;
  private ResourceType materialType;
  private ResourceRole helperRole;
  private ResourceRole cementRole;
  private ResourceRole bricksRole;
  private Resource anil;
  private Resource bablu;
  private Resource cement;
  private Resource bricks;
  private Activity excavation;
  private Activity brickMasonry;
  private Project project;

  @BeforeEach
  void setUp() {
    labourType = ResourceType.builder().code("LABOR").name("Manpower").build();
    labourType.setId(UUID.randomUUID());
    materialType = ResourceType.builder().code("MATERIAL").name("Material").build();
    materialType.setId(UUID.randomUUID());

    helperRole = ResourceRole.builder().code("RM-HLP").name("Helper").productivityUnit("Day").build();
    helperRole.setId(UUID.randomUUID());
    cementRole = ResourceRole.builder().code("MR-CEM").name("Cement").productivityUnit("Bag").build();
    cementRole.setId(UUID.randomUUID());
    bricksRole = ResourceRole.builder().code("MR-BRK").name("Bricks").productivityUnit("Nos").build();
    bricksRole.setId(UUID.randomUUID());

    anil = Resource.builder().code("LAB-HLP-01").name("Anil Das").role(helperRole).resourceType(labourType).build();
    anil.setId(UUID.randomUUID());
    bablu = Resource.builder().code("LAB-HLP-02").name("Bablu Sahu").role(helperRole).resourceType(labourType).build();
    bablu.setId(UUID.randomUUID());
    cement = Resource.builder().code("MAT-CEM-01").name("Cement (PPC 50kg)").role(cementRole).resourceType(materialType).build();
    cement.setId(UUID.randomUUID());
    bricks = Resource.builder().code("MAT-BRK-01").name("Bricks (red)").role(bricksRole).resourceType(materialType).build();
    bricks.setId(UUID.randomUUID());

    excavation = new Activity();
    excavation.setId(UUID.randomUUID());
    excavation.setCode("A-002");
    excavation.setName("Excavation");
    excavation.setPlannedStartDate(LocalDate.of(2026, 5, 4));
    excavation.setPlannedFinishDate(LocalDate.of(2026, 5, 12));

    brickMasonry = new Activity();
    brickMasonry.setId(UUID.randomUUID());
    brickMasonry.setCode("A-010");
    brickMasonry.setName("Brick masonry (walls)");
    brickMasonry.setPlannedStartDate(LocalDate.of(2026, 5, 4));
    brickMasonry.setPlannedFinishDate(LocalDate.of(2026, 5, 8));

    project = new Project();
    project.setId(projectId);
    project.setCalendarId(calendarId);
    project.setPlannedStartDate(LocalDate.of(2026, 5, 1));
    project.setPlannedFinishDate(LocalDate.of(2026, 6, 30));
  }

  /** Assignment with explicit dates on the assignment row. */
  private ResourceAssignment assignment(Resource resource, Activity activity, double plannedUnits, LocalDate start, LocalDate finish) {
    ResourceAssignment a = ResourceAssignment.builder()
        .activityId(activity.getId())
        .projectId(projectId)
        .resourceId(resource.getId())
        .roleId(null)
        .plannedUnits(plannedUnits)
        .plannedStartDate(start)
        .plannedFinishDate(finish)
        .build();
    a.setId(UUID.randomUUID());
    return a;
  }

  /** Assignment with NULL planned dates — the common seeded shape that needs the activity-date fallback. */
  private ResourceAssignment assignmentNoDates(Resource resource, Activity activity, double plannedUnits) {
    ResourceAssignment a = ResourceAssignment.builder()
        .activityId(activity.getId())
        .projectId(projectId)
        .resourceId(resource.getId())
        .roleId(null)
        .plannedUnits(plannedUnits)
        .plannedStartDate(null)
        .plannedFinishDate(null)
        .build();
    a.setId(UUID.randomUUID());
    return a;
  }

  private DailyActivityResourceOutput dailyOutput(Resource resource, Activity activity, LocalDate date, double qty) {
    DailyActivityResourceOutput o = DailyActivityResourceOutput.builder()
        .projectId(projectId)
        .activityId(activity.getId())
        .resourceId(resource.getId())
        .outputDate(date)
        .qtyExecuted(BigDecimal.valueOf(qty))
        .unit("PER_DAY")
        .build();
    o.setId(UUID.randomUUID());
    return o;
  }

  private void mockMonFriCalendar() {
    // Build a real snapshot with Mon-Fri WORKING, Sat-Sun NON_WORKING. No exceptions —
    // the snapshot's in-memory isWorkingDay/countWorkingDays do all the work.
    Map<DayOfWeek, CalendarWorkWeek> workWeekByDay = new HashMap<>();
    for (DayOfWeek dow : DayOfWeek.values()) {
      workWeekByDay.put(dow, CalendarWorkWeek.builder()
          .calendarId(calendarId)
          .dayOfWeek(dow)
          .dayType(dow.getValue() <= 5 ? DayType.WORKING : DayType.NON_WORKING)
          .totalWorkHours(dow.getValue() <= 5 ? 8.0 : 0.0)
          .build());
    }
    CalendarSnapshot snapshot = new CalendarSnapshot(calendarId, workWeekByDay, Map.of());
    when(calendarService.loadSnapshot(eq(calendarId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(snapshot);
  }

  private void mockNoActuals() {
    when(dailyOutputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
        eq(projectId), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
  }

  @Test
  @DisplayName("single assignment within one month: all planned units in that month, no actuals")
  void singleAssignmentSingleMonth() {
    ResourceAssignment a = assignment(anil, excavation, 7.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 12));
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(a));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(anil));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockMonFriCalendar();
    mockNoActuals();

    ResourceUsageTimePhasedResponse out = service.getTimePhased(projectId, null, null);

    ActivityUsage act = out.resourceTypes().get(0).resources().get(0).activities().get(0);
    assertThat(act.plannedByPeriod()).containsEntry("2026-05", 7.0);
    assertThat(act.actualByPeriod()).isEmpty();
  }

  @Test
  @DisplayName("falls back to activity dates when assignment row has null planned dates")
  void fallsBackToActivityDates() {
    ResourceAssignment a = assignmentNoDates(anil, excavation, 9.0);
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(a));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(anil));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockMonFriCalendar();
    mockNoActuals();

    ActivityUsage act = service.getTimePhased(projectId, null, null)
        .resourceTypes().get(0).resources().get(0).activities().get(0);

    assertThat(act.plannedByPeriod().get("2026-05")).as("activity dates 2026-05-04 to 2026-05-12 must drive the spread")
        .isEqualTo(9.0);
  }

  @Test
  @DisplayName("daily outputs are bucketed into actualByPeriod and merged into the tree")
  void actualsAreBucketed() {
    ResourceAssignment a = assignment(bablu, excavation, 7.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 12));
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(a));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(bablu));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockMonFriCalendar();
    when(dailyOutputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
        eq(projectId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(dailyOutput(bablu, excavation, LocalDate.of(2026, 5, 1), 1.0)));

    ResourceTypeUsage type = service.getTimePhased(projectId, null, null).resourceTypes().get(0);
    ResourceUsage res = type.resources().get(0);
    ActivityUsage act = res.activities().get(0);

    assertThat(act.plannedByPeriod()).containsEntry("2026-05", 7.0);
    assertThat(act.actualByPeriod()).containsEntry("2026-05", 1.0);

    // Resource and type rollups sum the actuals too.
    assertThat(res.actualByPeriod().get("2026-05")).isEqualTo(1.0);
    assertThat(type.actualByPeriod().get("2026-05")).isEqualTo(1.0);
  }

  @Test
  @DisplayName("assignment spanning two months: planned units split proportionally to working days")
  void assignmentSpansTwoMonths() {
    // 2026-05-25 (Mon) through 2026-06-05 (Fri) = 5 May working days + 5 Jun working days = 10
    ResourceAssignment a = assignment(anil, excavation, 20.0, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 5));
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(a));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(anil));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockMonFriCalendar();
    mockNoActuals();

    ActivityUsage act = service.getTimePhased(projectId, null, null)
        .resourceTypes().get(0).resources().get(0).activities().get(0);

    assertThat(act.plannedByPeriod().get("2026-05")).isEqualTo(10.0);
    assertThat(act.plannedByPeriod().get("2026-06")).isEqualTo(10.0);
  }

  @Test
  @DisplayName("type aggregation: planned and actual both blank when child resources have differing units")
  void mixedUnitTypeRowIsBlank() {
    ResourceAssignment cementAssignment = assignment(cement, brickMasonry, 80.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 8));
    ResourceAssignment bricksAssignment = assignment(bricks, brickMasonry, 15000.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 8));

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(cementAssignment, bricksAssignment));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(cement, bricks));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(cementRole, bricksRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(brickMasonry));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(materialType));
    mockMonFriCalendar();
    mockNoActuals();

    ResourceTypeUsage type = service.getTimePhased(projectId, null, null).resourceTypes().get(0);
    assertThat(type.unit()).isNull();
    assertThat(type.plannedByPeriod()).isNull();
    assertThat(type.actualByPeriod()).isNull();

    ResourceUsage cementRow = type.resources().stream()
        .filter(r -> r.resourceName().equals("Cement (PPC 50kg)")).findFirst().orElseThrow();
    assertThat(cementRow.unit()).isEqualTo("Bag");
    assertThat(cementRow.plannedByPeriod().get("2026-05")).isEqualTo(80.0);
  }

  @Test
  @DisplayName("type aggregation: sums planned and actual when all child resources share a unit")
  void uniformUnitTypeRowAggregates() {
    ResourceAssignment anilA = assignment(anil, excavation, 7.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 12));
    ResourceAssignment babluA = assignment(bablu, excavation, 7.0, LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 12));

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(anilA, babluA));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(anil, bablu));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockMonFriCalendar();
    when(dailyOutputRepository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(
        eq(projectId), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(List.of(
            dailyOutput(anil, excavation, LocalDate.of(2026, 5, 1), 0.5),
            dailyOutput(bablu, excavation, LocalDate.of(2026, 5, 1), 1.0)));

    ResourceTypeUsage type = service.getTimePhased(projectId, null, null).resourceTypes().get(0);
    assertThat(type.unit()).isEqualTo("Day");
    assertThat(type.plannedByPeriod().get("2026-05")).isEqualTo(14.0);
    assertThat(type.actualByPeriod().get("2026-05")).isEqualTo(1.5);
  }

  @Test
  @DisplayName("project with no plannedStartDate falls back to MIN/MAX of activity dates")
  void fallsBackToActivityBoundsWhenProjectDatesMissing() {
    project.setPlannedStartDate(null);
    project.setPlannedFinishDate(null);

    Activity activityForBounds = new Activity();
    activityForBounds.setId(UUID.randomUUID());
    activityForBounds.setProjectId(projectId);
    activityForBounds.setName("Earliest activity");
    activityForBounds.setPlannedStartDate(LocalDate.of(2026, 7, 1));
    activityForBounds.setPlannedFinishDate(LocalDate.of(2026, 8, 15));

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(activityForBounds));
    mockNoActuals();

    ResourceUsageTimePhasedResponse out = service.getTimePhased(projectId, null, null);

    assertThat(out.fromDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(out.toDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(out.periods()).containsExactly("2026-07", "2026-08");
    assertThat(out.resourceTypes()).isEmpty();
  }

  @Test
  @DisplayName("empty project (no dates anywhere): returns empty payload without throwing")
  void emptyProjectGracefulEmpty() {
    project.setPlannedStartDate(null);
    project.setPlannedFinishDate(null);

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of());
    when(activityRepository.findByProjectId(projectId)).thenReturn(List.of());

    ResourceUsageTimePhasedResponse out = service.getTimePhased(projectId, null, null);

    assertThat(out.periods()).isEmpty();
    assertThat(out.resourceTypes()).isEmpty();
    assertThat(out.fromDate()).isNull();
    assertThat(out.toDate()).isNull();
  }

  @Test
  @DisplayName("missing calendar: planned units land in start month as a defensive fallback")
  void missingCalendarFallsBackToStartMonth() {
    project.setCalendarId(null);
    ResourceAssignment a = assignment(anil, excavation, 7.0, LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 5));

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(assignmentRepository.findByProjectId(projectId)).thenReturn(List.of(a));
    when(resourceRepository.findAllById(anyIterable())).thenReturn(List.of(anil));
    when(roleRepository.findAllById(anyIterable())).thenReturn(List.of(helperRole));
    when(activityRepository.findAllById(anyIterable())).thenReturn(List.of(excavation));
    when(resourceTypeRepository.findAllById(anyIterable())).thenReturn(List.of(labourType));
    mockNoActuals();

    ActivityUsage act = service.getTimePhased(projectId, null, null)
        .resourceTypes().get(0).resources().get(0).activities().get(0);

    assertThat(act.plannedByPeriod()).containsEntry("2026-05", 7.0).doesNotContainKey("2026-06");
  }
}
