package com.bipros.scheduling.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.calendar.application.service.CalendarService;
import com.bipros.calendar.application.service.CalendarSnapshot;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.scheduling.domain.algorithm.CalendarCalculator;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit-tests {@link SchedulingService#newCpmEvaluator} — the load-once / evaluate-many CPM seam.
 *
 * <p>Uses the same 13-mock / real-constructor pattern as the other SchedulingService*Test classes.
 * The calendar is stubbed via {@code calendarService.loadSnapshot} so that
 * {@link com.bipros.scheduling.infrastructure.adapter.SnapshotCalendarCalculator} treats every
 * day as a working day — making the CPM deterministic without touching any DB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulingService — newCpmEvaluator (load-once / evaluate-many CPM seam)")
class SchedulingServiceSimulateTest {

    @Mock private ScheduleResultRepository scheduleResultRepository;
    @Mock private ScheduleActivityResultRepository scheduleActivityResultRepository;
    @Mock private CalendarCalculator calendarCalculator;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityRelationshipRepository activityRelationshipRepository;
    @Mock private CalendarService calendarService;
    @Mock private PertEstimateService pertEstimateService;
    @Mock private ScheduleHealthService scheduleHealthService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectRepository projectRepository;
    @Mock private CalendarRepository calendarRepository;
    @Mock private ScheduleFailureRecorder failureRecorder;

    private SchedulingService service;

    /** A shared calendarId for both activities. */
    private UUID calendarId;

    /** 7-day all-working-day snapshot. */
    private CalendarSnapshot sevenDaySnapshot;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(
            scheduleResultRepository,
            scheduleActivityResultRepository,
            calendarCalculator,
            activityRepository,
            activityRelationshipRepository,
            calendarService,
            pertEstimateService,
            scheduleHealthService,
            auditService,
            eventPublisher,
            projectRepository,
            calendarRepository,
            failureRecorder
        );

        calendarId = UUID.randomUUID();

        Map<DayOfWeek, CalendarWorkWeek> workWeek = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            workWeek.put(d, CalendarWorkWeek.builder()
                .calendarId(calendarId)
                .dayOfWeek(d)
                .dayType(DayType.WORKING)
                .totalWorkHours(8.0)
                .build());
        }
        sevenDaySnapshot = new CalendarSnapshot(calendarId, workWeek, Collections.emptyMap());
    }

    @Test
    @DisplayName("newCpmEvaluator: empty overrides reproduce base span; override shrinks span and finish")
    void newCpmEvaluator_evaluateShortensSpanAndFinish() {
        UUID projectId = UUID.randomUUID();
        UUID act1Id = UUID.randomUUID();
        UUID act2Id = UUID.randomUUID();

        Activity act1 = buildActivity(act1Id, projectId, 10.0);
        Activity act2 = buildActivity(act2Id, projectId, 5.0);

        when(calendarService.loadSnapshot(eq(calendarId), any(), any()))
            .thenReturn(sevenDaySnapshot);
        when(activityRepository.findByProjectId(projectId))
            .thenReturn(List.of(act1, act2));
        when(activityRelationshipRepository.findByProjectId(projectId))
            .thenReturn(Collections.emptyList());
        when(pertEstimateService.getByActivities(any()))
            .thenReturn(Collections.emptyList());

        SchedulingService.CpmEvaluator evaluator = service.newCpmEvaluator(projectId);

        // Empty overrides: base span = max activity duration on a 7-day calendar (10 days)
        SchedulingService.CpmSimulation base = evaluator.evaluate(Map.of());
        assertEquals(10.0, base.finishSpanWorkingDays(), 0.001,
            "Empty overrides must reproduce the base finish span (10 days)");

        // Override act1 → 6: both acts start at projectStart, act1 EF at +6 wins; span = 6
        SchedulingService.CpmSimulation shortened = evaluator.evaluate(Map.of(act1Id, 6.0));
        assertTrue(shortened.finishSpanWorkingDays() < base.finishSpanWorkingDays(),
            "Override must shorten the finish span");
        assertEquals(6.0, shortened.finishSpanWorkingDays(), 0.001,
            "Overridden span must equal the new critical duration (6)");
        assertTrue(shortened.projectFinish().isBefore(base.projectFinish()),
            "Override must move the project finish earlier");
    }

    private Activity buildActivity(UUID id, UUID projectId, double originalDuration) {
        Activity a = new Activity();
        a.setId(id);
        a.setProjectId(projectId);
        a.setCode("A-" + id.toString().substring(0, 4));
        a.setName("Task " + id);
        a.setActivityType(ActivityType.TASK_DEPENDENT);
        a.setStatus(ActivityStatus.NOT_STARTED);
        a.setOriginalDuration(originalDuration);
        a.setCalendarId(calendarId);
        a.setPlannedStartDate(LocalDate.of(2026, 1, 5));
        return a;
    }
}
