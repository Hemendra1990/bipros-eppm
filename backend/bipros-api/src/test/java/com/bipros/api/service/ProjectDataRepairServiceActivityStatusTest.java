package com.bipros.api.service;

import com.bipros.activity.application.percent.PercentCompleteCalculator;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.api.dto.ActivityStatusCorrectionRequest;
import com.bipros.api.dto.ActivityStatusCorrectionResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectDataRepairService#correctActivityStatus}, the admin endpoint
 * that re-derives an activity's status/percentComplete from its own APPROVED DPR/BOQ data
 * using the same {@link PercentCompleteCalculator} engine the app already uses.
 */
@ExtendWith(MockitoExtension.class)
class ProjectDataRepairServiceActivityStatusTest {

  @Mock DailyProgressReportRepository dprRepo;
  @Mock ActivityRepository activityRepo;
  @Mock AuditService auditService;

  // Real calculator (no mocking framework wrapping needed — it's pure logic over the entity).
  PercentCompleteCalculator percentCompleteCalculator = new PercentCompleteCalculator();

  ProjectDataRepairService service;

  @BeforeEach
  void setUp() {
    // @InjectMocks can't be used here: the service has 20 final constructor deps and only 3
    // (+ the calculator) matter for this method. Build directly via the constructor, reading
    // field order off the class, and pass null for every dependency this method never touches.
    service = new ProjectDataRepairService(
        dprRepo,                    // dprRepo
        activityRepo,                // activityRepo
        null,                         // activitySupervisorRepo
        null,                         // dprManpowerRepo
        null,                         // manpowerRoleRateRepo
        null,                         // equipmentRoleVariantRepo
        null,                         // materialRoleVariantRepo
        null,                         // manpowerCategoryMasterRepo
        null,                         // gradeMasterRepo
        null,                         // workActivityRepo
        null,                         // dprEquipmentRepo
        null,                         // normResolver
        null,                         // rateResolver
        null,                         // resourceAssignmentRepo
        null,                         // boqRebuildService
        null,                         // dprService
        null,                         // dbsAggregationService
        null,                         // scAssignmentRepo
        percentCompleteCalculator,    // percentCompleteCalculator
        auditService                  // auditService
    );
  }

  private Activity activity(UUID id, UUID projectId) {
    Activity a = new Activity();
    a.setId(id);
    a.setProjectId(projectId);
    a.setCode("A-1");
    a.setName("Earthwork");
    a.setEditStatus(ActivityEditStatus.DRAFT);
    return a;
  }

  @Test
  void noApprovedDprResetsToNotStarted() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(40.0);
    a.setActualStartDate(LocalDate.of(2026, 1, 1));
    a.setActualFinishDate(null);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId)).thenReturn(Optional.empty());

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    assertThat(resp.results()).hasSize(1);
    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("RESET_NOT_STARTED");
    assertThat(resp.summary().resetNotStarted()).isEqualTo(1);

    assertThat(a.getStatus()).isEqualTo(ActivityStatus.NOT_STARTED);
    assertThat(a.getPercentComplete()).isEqualTo(0.0);
    assertThat(a.getActualStartDate()).isNull();
    assertThat(a.getActualFinishDate()).isNull();
    verify(activityRepo).save(a);
  }

  @Test
  void boqPartialWorkdoneResetsToInProgressAndStampsActualStart() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    LocalDate earliest = LocalDate.of(2026, 2, 1);

    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);
    a.setActualStartDate(null); // no actual start yet
    a.setActualFinishDate(LocalDate.of(2026, 2, 10));

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId)).thenReturn(Optional.of(earliest));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("250"));

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("RESET_IN_PROGRESS");
    assertThat(r.newPercent()).isEqualTo(25.0);
    assertThat(resp.summary().resetInProgress()).isEqualTo(1);

    assertThat(a.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    assertThat(a.getPercentComplete()).isEqualTo(25.0);
    assertThat(a.getActualFinishDate()).isNull();
    assertThat(a.getActualStartDate()).isEqualTo(earliest);
    verify(activityRepo).save(a);
  }

  @Test
  void boqFullWorkdoneKeepsCompletedAndNeverSaves() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);
    a.setActualStartDate(LocalDate.of(2026, 1, 1));
    a.setActualFinishDate(LocalDate.of(2026, 1, 20));

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId))
        .thenReturn(Optional.of(LocalDate.of(2026, 1, 1)));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("1000"));

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("KEPT_COMPLETED");
    assertThat(resp.summary().keptCompleted()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void wrongProjectIsSkippedAndNeverSaves() {
    UUID projectId = UUID.randomUUID();
    UUID otherProjectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, otherProjectId);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(50.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_WRONG_PROJECT");
    assertThat(r.oldStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void dryRunDoesNotMutateEntityOrSaveOrAudit() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    LocalDate earliest = LocalDate.of(2026, 2, 1);

    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);
    a.setActualStartDate(null);
    a.setActualFinishDate(LocalDate.of(2026, 2, 10));

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId)).thenReturn(Optional.of(earliest));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("250"));

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(true);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("RESET_IN_PROGRESS");
    assertThat(r.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.newPercent()).isEqualTo(25.0);

    // The entity itself must be untouched — dryRun must never call a setter on a managed entity.
    assertThat(a.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    assertThat(a.getPercentComplete()).isEqualTo(100.0);
    assertThat(a.getActualStartDate()).isNull();
    assertThat(a.getActualFinishDate()).isEqualTo(LocalDate.of(2026, 2, 10));

    verify(activityRepo, never()).save(any());
    verifyNoInteractions(auditService);
  }

  @Test
  void noBoqDurationActivityRecomputesFromType() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setPercentCompleteType(PercentCompleteType.DURATION);
    a.setStatus(ActivityStatus.NOT_STARTED);
    a.setPercentComplete(0.0);
    a.setActualStartDate(LocalDate.now().minusDays(5));
    a.setActualFinishDate(null);
    a.setOriginalDuration(10.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId))
        .thenReturn(Optional.of(LocalDate.now().minusDays(5)));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(BigDecimal.ZERO);
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(BigDecimal.ZERO);

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("RESET_FROM_TYPE");
    assertThat(r.newPercent()).isEqualTo(50.0); // 5 elapsed / 10 originalDuration * 100
    assertThat(resp.summary().noBoqRecomputed()).isEqualTo(1);
    assertThat(a.getPercentComplete()).isEqualTo(50.0);
    assertThat(a.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    verify(activityRepo).save(a);
  }

  @Test
  void noBoqDurationActivityReaching100PercentStampsActualFinishDate() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setPercentCompleteType(PercentCompleteType.DURATION);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(50.0);
    a.setActualStartDate(LocalDate.now().minusDays(10));
    a.setActualFinishDate(null);
    a.setOriginalDuration(10.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId))
        .thenReturn(Optional.of(LocalDate.now().minusDays(10)));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(BigDecimal.ZERO);
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(BigDecimal.ZERO);

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("RESET_FROM_TYPE");
    assertThat(resp.summary().noBoqRecomputed()).isEqualTo(1);
    assertThat(a.getPercentComplete()).isEqualTo(100.0);
    assertThat(a.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    assertThat(a.getActualFinishDate()).isNotNull();
    verify(activityRepo).save(a);
  }

  @Test
  void noBoqUnitsActivityWithoutSumsIsSkipped() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setPercentCompleteType(PercentCompleteType.UNITS);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(30.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.findEarliestApprovedReportDateForActivity(activityId))
        .thenReturn(Optional.of(LocalDate.now().minusDays(3)));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(BigDecimal.ZERO);
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(BigDecimal.ZERO);
    // No plannedUnitsSum/actualUnitsSum passed to calculate(...) (both null) -> KEEP_PRIOR for UNITS.

    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of(activityId));
    req.setDryRun(false);

    ActivityStatusCorrectionResponse resp = service.correctActivityStatus(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NO_BOQ_NO_RECOMPUTE");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void emptyActivityIdsThrows() {
    ActivityStatusCorrectionRequest req = new ActivityStatusCorrectionRequest();
    req.setActivityIds(List.of());

    assertThatBusinessRuleException(() -> service.correctActivityStatus(UUID.randomUUID(), req));
  }

  private static void assertThatBusinessRuleException(Runnable action) {
    try {
      action.run();
    } catch (BusinessRuleException e) {
      return;
    }
    throw new AssertionError("Expected BusinessRuleException but none was thrown");
  }
}
