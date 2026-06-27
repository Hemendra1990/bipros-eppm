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
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.scheduling.application.dto.CompressionAnalysisResponse;
import com.bipros.scheduling.domain.algorithm.CalendarCalculator;
import com.bipros.scheduling.domain.algorithm.ScheduledActivity;
import com.bipros.scheduling.domain.model.CompressionAnalysis;
import com.bipros.scheduling.domain.repository.CompressionAnalysisRepository;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import com.bipros.scheduling.domain.repository.ScheduleResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleCompressionService — iterative crash + fast-track")
class ScheduleCompressionServiceTest {

    @Mock private CompressionAnalysisRepository compressionAnalysisRepository;
    @Mock private ScheduleResultRepository scheduleResultRepository;
    @Mock private ScheduleActivityResultRepository scheduleActivityResultRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityRelationshipRepository activityRelationshipRepository;
    @Mock private AuditService auditService;
    @Mock private ResourceAssignmentRepository resourceAssignmentRepository;
    @Mock private SchedulingService schedulingService;

    private ScheduleCompressionService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleCompressionService(
            compressionAnalysisRepository,
            scheduleResultRepository,
            scheduleActivityResultRepository,
            activityRepository,
            activityRelationshipRepository,
            auditService,
            resourceAssignmentRepository,
            schedulingService
        );
    }

    private void stubSave() {
        when(compressionAnalysisRepository.save(any())).thenAnswer(inv -> {
            CompressionAnalysis arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });
    }

    private Activity activityWith(UUID id, String code, double originalDuration) {
        Activity a = new Activity();
        a.setId(id);
        a.setCode(code);
        a.setOriginalDuration(originalDuration);
        return a;
    }

    private ScheduledActivity criticalSa(UUID activityId, double remainingDuration) {
        ScheduledActivity sa = new ScheduledActivity(activityId, remainingDuration);
        sa.setCritical(true);
        sa.setEarlyStart(LocalDate.of(2026, 1, 5));
        sa.setEarlyFinish(LocalDate.of(2026, 1, 5).plusDays((long) remainingDuration));
        return sa;
    }

    // -------------------------------------------------------------------------
    // Test 1: crash cost derived from real budgeted resource cost
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("crashCost_derivesFromBudgetedResourceCost: cost = (budgetedCost/origDur)*0.5*totalCrashed")
    void crashCost_derivesFromBudgetedResourceCost() {
        UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        // originalDuration=71, floor=35.5, budgetedCost=26975.52
        // costPerDay = (26975.52/71)*0.5 = 189.9684...
        // Loop crashes 35 full days + 0.5 partial → totalCrashed=35.5
        // additionalCost = round2(35.5 * 189.9684...) ≈ 6743.88
        Activity act = activityWith(activityId, "ACT-001", 71.0);
        act.setStatus(ActivityStatus.NOT_STARTED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations()).thenReturn(Map.of(activityId, 71.0));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(activityId))
            .thenReturn(new BigDecimal("26975.52"));

        // Each evaluate call returns a simulation with decreasing span and a critical activity.
        // The loop terminates naturally when overrides[a] reaches floor (35.5) — candidates empty.
        AtomicInteger evalCount = new AtomicInteger(0);
        when(mockEvaluator.evaluate(any())).thenAnswer(inv -> {
            int n = evalCount.getAndIncrement();
            double span = 71.0 - n;
            ScheduledActivity sa = criticalSa(activityId, span);
            return new SchedulingService.CpmSimulation(
                LocalDate.of(2026, 1, 5).plusDays(71L - n), span, List.of(sa));
        });
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        assertNotNull(response.recommendations());
        assertEquals(1, response.recommendations().size());
        BigDecimal additionalCost = response.recommendations().get(0).additionalCost();
        assertEquals(6743.88, additionalCost.doubleValue(), 0.02,
            "additionalCost must be derived from real budgeted resource cost * 0.5 * totalCrashed");
    }

    // -------------------------------------------------------------------------
    // Test 2: durationSaved reflects real finish-span reduction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("crashSavings_reflectsFinishSpanReduction: durationSaved = originalSpan - compressedSpan")
    void crashSavings_reflectsFinishSpanReduction() {
        UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Activity act = activityWith(activityId, "ACT-002", 81.0);
        act.setStatus(ActivityStatus.NOT_STARTED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations()).thenReturn(Map.of(activityId, 81.0));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(activityId))
            .thenReturn(BigDecimal.ZERO);

        // After 21 iterations the evaluator returns no critical activities → loop breaks
        AtomicInteger evalCount = new AtomicInteger(0);
        when(mockEvaluator.evaluate(any())).thenAnswer(inv -> {
            int n = evalCount.getAndIncrement();
            double span = 81.0 - n;
            List<ScheduledActivity> acts = n < 21
                ? List.of(criticalSa(activityId, span))
                : Collections.emptyList();
            return new SchedulingService.CpmSimulation(
                LocalDate.of(2026, 1, 5).plusDays(81L - n), span, acts);
        });
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        assertEquals(21.0, response.durationSaved(), 0.001,
            "durationSaved must equal originalSpan(81) minus compressedSpan(60)");
        assertEquals(60.0, response.compressedDuration(), 0.001,
            "compressedDuration must reflect the CPM result (60)");
    }

    // -------------------------------------------------------------------------
    // Test 3: COMPLETED activity excluded from crash candidates
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("completedActivity_excludedFromCrashCandidates: COMPLETED critical activity → no recommendation")
    void completedActivity_excludedFromCrashCandidates() {
        UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Activity act = activityWith(activityId, "ACT-DONE", 50.0);
        act.setStatus(ActivityStatus.COMPLETED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations()).thenReturn(Map.of(activityId, 50.0));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(activityId))
            .thenReturn(BigDecimal.ZERO);

        ScheduledActivity critSa = criticalSa(activityId, 50.0);
        when(mockEvaluator.evaluate(any())).thenReturn(
            new SchedulingService.CpmSimulation(LocalDate.of(2026, 7, 26), 50.0, List.of(critSa)));
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        assertTrue(response.recommendations().isEmpty(),
            "COMPLETED critical activity must not produce a crash recommendation");
        assertEquals(0.0, response.durationSaved(), 0.001,
            "durationSaved must be zero when only candidate is COMPLETED");
        assertEquals(response.originalFinishDate(), response.compressedFinishDate(),
            "Finish dates must be equal when nothing is crashed");
    }

    // -------------------------------------------------------------------------
    // Test 4: zero crash cost when activity has no resource assignments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("crashCost_zeroWhenNoResourceAssignments: additionalCost is zero, recommendation still present")
    void crashCost_zeroWhenNoResourceAssignments() {
        UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        // duration=4, floor=2 → 2 full iterations, then candidates empty
        Activity act = activityWith(activityId, "ACT-003", 4.0);
        act.setStatus(ActivityStatus.NOT_STARTED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations()).thenReturn(Map.of(activityId, 4.0));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(activityId))
            .thenReturn(BigDecimal.ZERO);

        AtomicInteger evalCount = new AtomicInteger(0);
        when(mockEvaluator.evaluate(any())).thenAnswer(inv -> {
            int n = evalCount.getAndIncrement();
            double span = 4.0 - n;
            ScheduledActivity sa = criticalSa(activityId, span);
            return new SchedulingService.CpmSimulation(
                LocalDate.of(2026, 1, 5).plusDays(4L - n), span, List.of(sa));
        });
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        assertNotNull(response.recommendations());
        assertEquals(1, response.recommendations().size(),
            "Must have one recommendation (activity was crashed)");
        assertEquals(0, response.recommendations().get(0).additionalCost().compareTo(BigDecimal.ZERO),
            "additionalCost must be zero when there are no resource assignments");
    }

    // -------------------------------------------------------------------------
    // Test 5: iterative crash on parallel fixture — real finish reduction, not CPL sum
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("iterativeCrash_parallelActivities: durationSaved = finish reduction, NOT sum of crashed durations")
    void iterativeCrash_parallelActivities() {
        UUID projectId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID a3 = UUID.randomUUID();

        // 3 parallel activities of 10 days each, all crashable to floor=5
        Activity act1 = activityWith(a1, "A1", 10.0);
        Activity act2 = activityWith(a2, "A2", 10.0);
        Activity act3 = activityWith(a3, "A3", 10.0);
        for (Activity a : List.of(act1, act2, act3)) a.setStatus(ActivityStatus.NOT_STARTED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations())
            .thenReturn(Map.of(a1, 10.0, a2, 10.0, a3, 10.0));
        when(activityRepository.findByProjectId(projectId))
            .thenReturn(List.of(act1, act2, act3));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(any()))
            .thenReturn(BigDecimal.ZERO);

        // Each iteration all 3 are critical; finish span reduces by 1 per iteration.
        // After 5 iterations all activities are at floor → candidates empty → break.
        AtomicInteger evalCount = new AtomicInteger(0);
        when(mockEvaluator.evaluate(any())).thenAnswer(inv -> {
            int n = evalCount.getAndIncrement();
            double span = 10.0 - n;
            return new SchedulingService.CpmSimulation(
                LocalDate.of(2026, 1, 5).plusDays(10L - n), span,
                List.of(criticalSa(a1, span), criticalSa(a2, span), criticalSa(a3, span)));
        });
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        // Finish goes from 10 to 5 = 5 days saved (NOT 15 = 3 activities × 5 days each)
        assertEquals(5.0, response.durationSaved(), 0.001,
            "durationSaved must equal the real finish reduction (5), not the CPL sum (15)");
        assertEquals(3, response.recommendations().size(),
            "All 3 parallel activities should appear in recommendations");
        response.recommendations().forEach(rec ->
            assertEquals(5.0, rec.durationSaved(), 0.001,
                "Each activity must have been crashed by 5 days"));
    }

    // -------------------------------------------------------------------------
    // Test 6: revert-on-no-improvement → 0 cost, 0 durationSaved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("revertOnNoImprovement: non-improving step is reverted, recommendations empty")
    void revertOnNoImprovement() {
        UUID projectId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        Activity act = activityWith(activityId, "ACT-X", 10.0);
        act.setStatus(ActivityStatus.NOT_STARTED);

        SchedulingService.CpmEvaluator mockEvaluator = mock(SchedulingService.CpmEvaluator.class);
        when(schedulingService.newCpmEvaluator(projectId)).thenReturn(mockEvaluator);
        when(mockEvaluator.baseSchedulingDurations()).thenReturn(Map.of(activityId, 10.0));
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(act));
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(activityId))
            .thenReturn(new BigDecimal("100.00"));

        ScheduledActivity critSa = criticalSa(activityId, 10.0);
        // Base returns span=10; iteration 1 also returns span=10 → no improvement → revert + break
        when(mockEvaluator.evaluate(any())).thenReturn(
            new SchedulingService.CpmSimulation(LocalDate.of(2026, 1, 15), 10.0, List.of(critSa)),
            new SchedulingService.CpmSimulation(LocalDate.of(2026, 1, 15), 10.0, List.of(critSa))
        );
        stubSave();

        CompressionAnalysisResponse response = service.analyzeCrashing(projectId);

        assertTrue(response.recommendations().isEmpty(),
            "No recommendations when crashing does not improve the finish (step reverted)");
        assertEquals(0.0, response.durationSaved(), 0.001,
            "durationSaved must be zero when the only step was reverted");
        assertEquals(BigDecimal.ZERO, response.additionalCost(),
            "totalAdditionalCost must be zero when step is reverted");
        assertEquals(response.originalFinishDate(), response.compressedFinishDate(),
            "Finish dates must be equal when nothing was gained");
    }

    // -------------------------------------------------------------------------
    // Test 8: real-CPM plateau — COMPLETED activity's actualFinishDate binds the project finish
    // so crashing stops at the plateau, NOT at the crashable floor, and the wasteful
    // final non-improving step is reverted. This test uses a REAL SchedulingService
    // (no stub on newCpmEvaluator / evaluate) to reproduce the natural plateau.
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("realCpm_plateau: COMPLETED activity's fixed finish binds project — crashing stops at plateau, not at floor")
    void realCpm_plateau_completedActivityBindsFinish() {
        UUID projectId = UUID.randomUUID();
        UUID actAId   = UUID.randomUUID();
        UUID actBId   = UUID.randomUUID();
        UUID calId    = UUID.randomUUID();
        LocalDate projectStart = LocalDate.of(2026, 1, 5);

        // Activity A — NOT_STARTED, duration=20, floor=10 (crashable by up to 10 days).
        // On the 7-day all-working calendar it finishes at projectStart + 20 working days = Jan 25.
        Activity actA = new Activity();
        actA.setId(actAId);
        actA.setCode("A-CRASH");
        actA.setActivityType(ActivityType.TASK_DEPENDENT);
        actA.setStatus(ActivityStatus.NOT_STARTED);
        actA.setOriginalDuration(20.0);
        actA.setCalendarId(calId);
        actA.setPlannedStartDate(projectStart);

        // Activity B — COMPLETED, actualFinishDate = Jan 20 (= 15 working days from Jan 5).
        // Plateau: once A is crashed to 15 days (earlyFinish = Jan 20), further crashing A
        // leaves B's fixed Jan-20 finish as the sole project driver — no span improvement —
        // so the algorithm reverts that final step and stops at 15 days of total savings.
        // A's fully-crashed finish would be Jan 15 (= 10 working days from Jan 5), which is
        // BEFORE B's fixed finish, proving the plateau is reached before the floor.
        Activity actB = new Activity();
        actB.setId(actBId);
        actB.setCode("B-DONE");
        actB.setActivityType(ActivityType.TASK_DEPENDENT);
        actB.setStatus(ActivityStatus.COMPLETED);
        actB.setOriginalDuration(15.0);
        actB.setCalendarId(calId);
        actB.setPlannedStartDate(projectStart);
        actB.setActualStartDate(projectStart);               // prevents calendarCalculator call in effectiveActualStart
        actB.setActualFinishDate(LocalDate.of(2026, 1, 20)); // CPM locks B's finish here (RETAINED_LOGIC)

        // 7-day all-working-day snapshot (same pattern as SchedulingServiceSimulateTest)
        Map<DayOfWeek, CalendarWorkWeek> workWeek = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            workWeek.put(d, CalendarWorkWeek.builder()
                .calendarId(calId).dayOfWeek(d)
                .dayType(DayType.WORKING).totalWorkHours(8.0).build());
        }
        CalendarSnapshot sevenDaySnap = new CalendarSnapshot(calId, workWeek, Collections.emptyMap());

        // Local mocks for the real SchedulingService (isolated from other tests)
        CalendarService            localCalSvc   = mock(CalendarService.class);
        PertEstimateService        localPertSvc  = mock(PertEstimateService.class);
        ScheduleHealthService      localHealthSvc = mock(ScheduleHealthService.class);
        AuditService               localAuditSvc = mock(AuditService.class);
        ApplicationEventPublisher  localPub      = mock(ApplicationEventPublisher.class);
        ProjectRepository          localProjRepo = mock(ProjectRepository.class);
        CalendarRepository         localCalRepo  = mock(CalendarRepository.class);
        ScheduleFailureRecorder    localFailRec  = mock(ScheduleFailureRecorder.class);
        ScheduleResultRepository   localSrRepo   = mock(ScheduleResultRepository.class);
        ScheduleActivityResultRepository localSarRepo = mock(ScheduleActivityResultRepository.class);
        CalendarCalculator         localCalcCalc = mock(CalendarCalculator.class);

        // Stub snapshot so SnapshotCalendarCalculator treats every day as a working day
        when(localCalSvc.loadSnapshot(eq(calId), any(), any())).thenReturn(sevenDaySnap);

        // Both services share the class-level repository mocks
        when(activityRepository.findByProjectId(projectId)).thenReturn(List.of(actA, actB));
        when(activityRelationshipRepository.findByProjectId(projectId)).thenReturn(Collections.emptyList());
        when(localPertSvc.getByActivities(any())).thenReturn(Collections.emptyList());
        when(localProjRepo.findById(projectId)).thenReturn(Optional.empty()); // projectStart derived from activities
        when(resourceAssignmentRepository.sumBudgetedCostByActivityId(any())).thenReturn(BigDecimal.ZERO);

        SchedulingService realSched = new SchedulingService(
            localSrRepo, localSarRepo, localCalcCalc,
            activityRepository, activityRelationshipRepository,
            localCalSvc, localPertSvc, localHealthSvc,
            localAuditSvc, localPub, localProjRepo, localCalRepo, localFailRec
        );

        ScheduleCompressionService localSvc = new ScheduleCompressionService(
            compressionAnalysisRepository, scheduleResultRepository,
            scheduleActivityResultRepository, activityRepository,
            activityRelationshipRepository, auditService,
            resourceAssignmentRepository, realSched
        );

        stubSave();

        CompressionAnalysisResponse response = localSvc.analyzeCrashing(projectId);

        // CPM trace on a 7-day calendar:
        //   Initial:  A earlyFinish = Jan 25 (20 days), B earlyFinish = Jan 20 (fixed). span = 20.
        //   Crash ×1: A → 19 days → Jan 24. span = 19. (improvement, keep)
        //   Crash ×2: A → 18       → Jan 23. span = 18. (improvement)
        //   ...
        //   Crash ×5: A → 15       → Jan 20. span = 15. (improvement, keep — A ties B)
        //   Crash ×6: A → 14       → Jan 19. B still Jan 20. span = 15. (NO improvement → REVERT)
        // durationSaved = 20 - 15 = 5. A's totalCrashed = 5. NOT crashed to floor (10).

        assertEquals(5.0, response.durationSaved(), 0.001,
            "durationSaved must equal the real finish-span reduction (5 days), NOT A's full crash range (10)");

        assertEquals(LocalDate.of(2026, 1, 20), response.compressedFinishDate(),
            "compressedFinishDate must equal B's fixed actualFinishDate (the plateau)");

        // A's fully-crashed finish (floor=10 working days from Jan 5) = Jan 15; plateau is Jan 20 (later).
        assertTrue(response.compressedFinishDate().isAfter(LocalDate.of(2026, 1, 5).plusDays(10)),
            "Plateau finish (Jan 20) must be AFTER A's fully-crashed finish (Jan 15), proving the plateau revert");

        // Only A must produce a recommendation; B is COMPLETED and excluded
        assertEquals(1, response.recommendations().size(),
            "Only activity A must appear in recommendations — B is COMPLETED");
        assertTrue(response.recommendations().stream().noneMatch(r -> actBId.equals(r.activityId())),
            "COMPLETED activity B must not appear in any recommendation");

        // A's recommendation must reflect only the useful crashing (5 days), not the full range (10)
        var recA = response.recommendations().get(0);
        assertEquals(actAId, recA.activityId());
        assertEquals(5.0, recA.durationSaved(), 0.001,
            "A must be crashed to the plateau only (5 days), not all the way to its floor (10 days)");
        assertEquals(15.0, recA.newDuration(), 0.001,
            "A's new duration must be 15 (the plateau), not 10 (the floor)");
    }

    // -------------------------------------------------------------------------
    // Test 7: fast-track returns zero savings when there are no relationships
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fastTrack_zeroWhenNoRelationships: empty recommendations, durationSaved=0")
    void fastTrack_zeroWhenNoRelationships() {
        UUID projectId = UUID.randomUUID();
        UUID scheduleResultId = UUID.randomUUID();

        com.bipros.scheduling.domain.model.ScheduleResult scheduleResult =
            com.bipros.scheduling.domain.model.ScheduleResult.builder()
                .criticalPathLength(100.0)
                .build();
        scheduleResult.setId(scheduleResultId);

        when(scheduleResultRepository.findTopByProjectIdOrderByCalculatedAtDesc(projectId))
            .thenReturn(Optional.of(scheduleResult));
        when(scheduleActivityResultRepository.findByScheduleResultId(scheduleResultId))
            .thenReturn(Collections.emptyList());
        when(activityRepository.findByProjectId(projectId))
            .thenReturn(Collections.emptyList());
        when(activityRelationshipRepository.findByProjectId(projectId))
            .thenReturn(Collections.emptyList());
        when(compressionAnalysisRepository.save(any())).thenAnswer(inv -> {
            CompressionAnalysis arg = inv.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        CompressionAnalysisResponse response = service.analyzeFastTrack(projectId);

        assertEquals(Collections.emptyList(), response.recommendations());
        assertEquals(0.0, response.durationSaved(), 0.001);
        assertEquals(100.0, response.compressedDuration(), 0.001);
    }
}
