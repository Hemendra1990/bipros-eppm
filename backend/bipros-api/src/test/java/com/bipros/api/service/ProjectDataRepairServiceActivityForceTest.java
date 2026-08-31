package com.bipros.api.service;

import com.bipros.activity.application.percent.PercentCompleteCalculator;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.api.dto.ActivityStatusForceRequest;
import com.bipros.api.dto.ActivityStatusForceResponse;
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
import java.util.Arrays;
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
 * Unit tests for {@link ProjectDataRepairService#forceActivityInProgress}, the admin endpoint
 * that force-sets genuinely-100% activities down to IN_PROGRESS at a caller-supplied percent,
 * WITHOUT touching DPR/BOQ data. Opposite of {@code correctActivityStatus}: that method
 * keeps genuine-100% activities completed; this one forces them down.
 */
@ExtendWith(MockitoExtension.class)
class ProjectDataRepairServiceActivityForceTest {

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
    return a;
  }

  private ActivityStatusForceRequest requestFor(boolean dryRun, UUID activityId, Double target) {
    ActivityStatusForceRequest req = new ActivityStatusForceRequest();
    ActivityStatusForceRequest.ForceTarget t = new ActivityStatusForceRequest.ForceTarget();
    t.setActivityId(activityId);
    t.setTargetPercent(target);
    req.setActivities(List.of(t));
    req.setDryRun(dryRun);
    return req;
  }

  @Test
  void genuine100PercentBoqIsForcedToInProgress() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate finish = LocalDate.of(2026, 1, 20);

    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);
    a.setActualStartDate(start);
    a.setActualFinishDate(finish);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("1000"));

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("FORCED_IN_PROGRESS");
    assertThat(r.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.newPercent()).isEqualTo(55.0);
    assertThat(resp.summary().forced()).isEqualTo(1);
    assertThat(resp.summary().skipped()).isEqualTo(0);
    assertThat(resp.dryRun()).isFalse();

    assertThat(a.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    assertThat(a.getPercentComplete()).isEqualTo(55.0);
    assertThat(a.getActualFinishDate()).isNull();
    assertThat(a.getActualStartDate()).isEqualTo(start); // unchanged

    verify(activityRepo).save(a);
    verify(auditService).logUpdate("Activity", activityId, "percentComplete", 100.0, 55.0);
    verify(auditService).logUpdate("Activity", activityId, "status", ActivityStatus.COMPLETED, ActivityStatus.IN_PROGRESS);
  }

  @Test
  void dryRunDoesNotMutateEntityOrSaveOrAudit() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate finish = LocalDate.of(2026, 1, 20);

    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);
    a.setActualStartDate(start);
    a.setActualFinishDate(finish);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("1000"));

    ActivityStatusForceRequest req = requestFor(true, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("FORCED_IN_PROGRESS");
    assertThat(r.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.newPercent()).isEqualTo(55.0);
    assertThat(resp.dryRun()).isTrue();

    // Entity itself must be untouched — dryRun must never call a setter on a managed entity.
    assertThat(a.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    assertThat(a.getPercentComplete()).isEqualTo(100.0);
    assertThat(a.getActualStartDate()).isEqualTo(start);
    assertThat(a.getActualFinishDate()).isEqualTo(finish);

    verify(activityRepo, never()).save(any());
    verifyNoInteractions(auditService);
  }

  @Test
  void derivedPercentBelow100IsSkippedNotCaseB() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(80.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("800"));

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NOT_CASE_B");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    assertThat(resp.summary().forced()).isEqualTo(0);
    verify(activityRepo, never()).save(any());
    verifyNoInteractions(auditService);
  }

  @Test
  void noApprovedBoqIsSkippedNotCaseBWhenZero() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(30.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(BigDecimal.ZERO);

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NOT_CASE_B");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void noApprovedBoqIsSkippedNotCaseBWhenNull() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.IN_PROGRESS);
    a.setPercentComplete(30.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(null);

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NOT_CASE_B");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void invalidTargetPercentsAreSkippedWithoutLoadingActivity() {
    UUID projectId = UUID.randomUUID();
    for (Double invalid : Arrays.asList(0.0, 100.0, 120.0, -5.0, null)) {
      UUID activityId = UUID.randomUUID();
      ActivityStatusForceRequest req = requestFor(false, activityId, invalid);

      ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

      ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
      assertThat(r.outcome()).isEqualTo("SKIPPED_INVALID_PERCENT");
      assertThat(resp.summary().skipped()).isEqualTo(1);
    }
    verifyNoInteractions(activityRepo);
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

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_WRONG_PROJECT");
    assertThat(r.oldStatus()).isEqualTo("IN_PROGRESS");
    assertThat(r.newStatus()).isEqualTo("IN_PROGRESS");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void notFoundIsSkipped() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(activityRepo.findById(activityId)).thenReturn(Optional.empty());

    ActivityStatusForceRequest req = requestFor(false, activityId, 55.0);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NOT_FOUND");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void nullActivityIdIsSkippedNotFoundWithoutCallingFindById() {
    UUID projectId = UUID.randomUUID();

    ActivityStatusForceRequest.ForceTarget t = new ActivityStatusForceRequest.ForceTarget();
    t.setActivityId(null);
    t.setTargetPercent(55.0);
    ActivityStatusForceRequest req = new ActivityStatusForceRequest();
    req.setActivities(List.of(t));
    req.setDryRun(false);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_NOT_FOUND");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    assertThat(resp.summary().forced()).isEqualTo(0);
    verify(activityRepo, never()).findById(any());
    verify(activityRepo, never()).save(any());
  }

  @Test
  void targetPercentRoundingToBoundaryIsSkippedInvalidPercent() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("1000"));

    ActivityStatusForceRequest req = requestFor(false, activityId, 99.999);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_INVALID_PERCENT");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    assertThat(resp.summary().forced()).isEqualTo(0);
    assertThat(a.getStatus()).isEqualTo(ActivityStatus.COMPLETED); // unchanged
    assertThat(a.getPercentComplete()).isEqualTo(100.0); // unchanged
    verify(activityRepo, never()).save(any());
  }

  @Test
  void targetPercentRoundingToLowerBoundaryIsSkippedInvalidPercent() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    Activity a = activity(activityId, projectId);
    a.setStatus(ActivityStatus.COMPLETED);
    a.setPercentComplete(100.0);

    when(activityRepo.findById(activityId)).thenReturn(Optional.of(a));
    when(dprRepo.sumLinkedBoqQtyApproved(activityId)).thenReturn(new BigDecimal("1000"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(activityId)).thenReturn(new BigDecimal("1000"));

    ActivityStatusForceRequest req = requestFor(false, activityId, 0.001);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    ActivityStatusCorrectionResponse.Result r = resp.results().get(0);
    assertThat(r.outcome()).isEqualTo("SKIPPED_INVALID_PERCENT");
    assertThat(resp.summary().skipped()).isEqualTo(1);
    assertThat(resp.summary().forced()).isEqualTo(0);
    verify(activityRepo, never()).save(any());
  }

  @Test
  void emptyActivitiesThrows() {
    ActivityStatusForceRequest req = new ActivityStatusForceRequest();
    req.setActivities(List.of());

    assertThatBusinessRuleException(() -> service.forceActivityInProgress(UUID.randomUUID(), req));
  }

  @Test
  void nullActivitiesThrows() {
    ActivityStatusForceRequest req = new ActivityStatusForceRequest();
    req.setActivities(null);

    assertThatBusinessRuleException(() -> service.forceActivityInProgress(UUID.randomUUID(), req));
  }

  @Test
  void summaryCountsAreCorrectAcrossMixedBatch() {
    UUID projectId = UUID.randomUUID();

    // 1. Genuine 100% BOQ -> forced
    UUID forcedId = UUID.randomUUID();
    Activity forcedActivity = activity(forcedId, projectId);
    forcedActivity.setStatus(ActivityStatus.COMPLETED);
    forcedActivity.setPercentComplete(100.0);
    when(activityRepo.findById(forcedId)).thenReturn(Optional.of(forcedActivity));
    when(dprRepo.sumLinkedBoqQtyApproved(forcedId)).thenReturn(new BigDecimal("500"));
    when(dprRepo.sumActivityWorkdoneOnBoqApproved(forcedId)).thenReturn(new BigDecimal("500"));

    // 2. Invalid percent -> skipped, no load
    UUID invalidId = UUID.randomUUID();

    // 3. Not found -> skipped
    UUID notFoundId = UUID.randomUUID();
    when(activityRepo.findById(notFoundId)).thenReturn(Optional.empty());

    // 4. Wrong project -> skipped
    UUID wrongProjectId = UUID.randomUUID();
    Activity wrongProjectActivity = activity(wrongProjectId, UUID.randomUUID());
    when(activityRepo.findById(wrongProjectId)).thenReturn(Optional.of(wrongProjectActivity));

    // 5. Not case B (no approved boq) -> skipped
    UUID notCaseBId = UUID.randomUUID();
    Activity notCaseBActivity = activity(notCaseBId, projectId);
    when(activityRepo.findById(notCaseBId)).thenReturn(Optional.of(notCaseBActivity));
    when(dprRepo.sumLinkedBoqQtyApproved(notCaseBId)).thenReturn(BigDecimal.ZERO);

    ActivityStatusForceRequest req = new ActivityStatusForceRequest();
    ActivityStatusForceRequest.ForceTarget t1 = new ActivityStatusForceRequest.ForceTarget();
    t1.setActivityId(forcedId);
    t1.setTargetPercent(40.0);
    ActivityStatusForceRequest.ForceTarget t2 = new ActivityStatusForceRequest.ForceTarget();
    t2.setActivityId(invalidId);
    t2.setTargetPercent(0.0);
    ActivityStatusForceRequest.ForceTarget t3 = new ActivityStatusForceRequest.ForceTarget();
    t3.setActivityId(notFoundId);
    t3.setTargetPercent(40.0);
    ActivityStatusForceRequest.ForceTarget t4 = new ActivityStatusForceRequest.ForceTarget();
    t4.setActivityId(wrongProjectId);
    t4.setTargetPercent(40.0);
    ActivityStatusForceRequest.ForceTarget t5 = new ActivityStatusForceRequest.ForceTarget();
    t5.setActivityId(notCaseBId);
    t5.setTargetPercent(40.0);
    req.setActivities(List.of(t1, t2, t3, t4, t5));
    req.setDryRun(false);

    ActivityStatusForceResponse resp = service.forceActivityInProgress(projectId, req);

    assertThat(resp.summary().forced()).isEqualTo(1);
    assertThat(resp.summary().skipped()).isEqualTo(4);
    assertThat(resp.results()).hasSize(5);
    verify(activityRepo, never()).save(wrongProjectActivity);
    verify(activityRepo, never()).save(notCaseBActivity);
    verify(activityRepo).save(forcedActivity);
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
