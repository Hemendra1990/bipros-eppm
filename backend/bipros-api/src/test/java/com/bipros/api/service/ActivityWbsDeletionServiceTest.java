package com.bipros.api.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityCodeAssignmentRepository;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivityStepRepository;
import com.bipros.activity.domain.repository.ActivitySupervisorRepository;
import com.bipros.api.dto.DeleteActivitiesWbsRequest;
import com.bipros.api.dto.DeleteActivitiesWbsResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.service.WbsService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.risk.domain.repository.RiskActivityAssignmentRepository;
import com.bipros.scheduling.domain.repository.ScheduleActivityResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActivityWbsDeletionService#deleteActivitiesAndWbs}, the irreversible
 * admin endpoint's core service: resolves a WBS-subtree/activity-id target set, runs the
 * dry-run-first guard (DPRs / activity_steps / sub-contractor / risk assignments always abort;
 * relationships-to-kept / BOQ-mapped / locked abort unless {@code force}), and — only when
 * {@code !dryRun && !aborted} — deletes in cascade order. Mirrors the pure-Mockito style of
 * {@code UnitConsistencyRepairServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ActivityWbsDeletionServiceTest {

  @Mock WbsNodeRepository wbsNodeRepository;
  @Mock ActivityRepository activityRepository;
  @Mock ResourceAssignmentRepository resourceAssignmentRepository;
  @Mock ScheduleActivityResultRepository scheduleActivityResultRepository;
  @Mock ActivityCodeAssignmentRepository activityCodeAssignmentRepository;
  @Mock ActivityRelationshipRepository activityRelationshipRepository;
  @Mock ActivitySupervisorRepository activitySupervisorRepository;
  @Mock DailyProgressReportRepository dailyProgressReportRepository;
  @Mock ActivityStepRepository activityStepRepository;
  @Mock ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;
  @Mock RiskActivityAssignmentRepository riskActivityAssignmentRepository;
  @Mock BoqItemRepository boqItemRepository;
  @Mock WbsService wbsService;
  @Mock AuditService auditService;

  private ActivityWbsDeletionService newService() {
    return new ActivityWbsDeletionService(
        wbsNodeRepository, activityRepository, resourceAssignmentRepository,
        scheduleActivityResultRepository, activityCodeAssignmentRepository,
        activityRelationshipRepository, activitySupervisorRepository,
        dailyProgressReportRepository, activityStepRepository,
        activitySubContractorAssignmentRepository, riskActivityAssignmentRepository,
        boqItemRepository, wbsService, auditService);
  }

  // ---- builders ----

  private WbsNode wbsNode(UUID id, UUID projectId, UUID parentId) {
    WbsNode n = new WbsNode();
    n.setId(id);
    n.setProjectId(projectId);
    n.setParentId(parentId);
    n.setCode("W-" + id.toString().substring(0, 4));
    n.setName("Node " + id);
    return n;
  }

  private Activity activity(UUID id, UUID projectId, UUID wbsNodeId) {
    Activity a = new Activity();
    a.setId(id);
    a.setProjectId(projectId);
    a.setWbsNodeId(wbsNodeId);
    a.setCode("A-" + id.toString().substring(0, 4));
    return a;
  }

  private ActivityRelationship relationship(UUID id, UUID predecessorId, UUID successorId) {
    ActivityRelationship r = new ActivityRelationship();
    r.setId(id);
    r.setPredecessorActivityId(predecessorId);
    r.setSuccessorActivityId(successorId);
    return r;
  }

  private BoqItem boqItem(UUID id, String itemNo, UUID wbsNodeId) {
    BoqItem b = new BoqItem();
    b.setId(id);
    b.setItemNo(itemNo);
    b.setWbsNodeId(wbsNodeId);
    return b;
  }

  private DeleteActivitiesWbsRequest request(List<UUID> wbsNodeIds, List<UUID> activityIds,
                                              boolean dryRun, boolean force) {
    DeleteActivitiesWbsRequest req = new DeleteActivitiesWbsRequest();
    req.setWbsNodeIds(wbsNodeIds);
    req.setActivityIds(activityIds);
    req.setDryRun(dryRun);
    req.setForce(force);
    return req;
  }

  private void verifyNoWrites() {
    verify(resourceAssignmentRepository, never()).deleteByActivityIdIn(any());
    verify(scheduleActivityResultRepository, never()).deleteByActivityIdIn(any());
    verify(activityCodeAssignmentRepository, never()).deleteByActivityIdIn(any());
    verify(activityRelationshipRepository, never()).deleteAllById(any());
    verify(activitySupervisorRepository, never()).deleteByActivityIdIn(any());
    verify(activityRepository, never()).deleteAllById(any());
    verify(boqItemRepository, never()).nullWbsNodeByWbsNodeIdIn(any());
    verify(wbsService, never()).deleteNode(any());
    verifyNoInteractions(auditService);
  }

  // ---- 1. Subtree recursion + explicit activityIds unioned ----

  @Test
  void subtreeRecursionCollectsDescendantsAndUnionsExplicitActivityIds() {
    UUID projectId = UUID.randomUUID();
    UUID root = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    UUID grandchild = UUID.randomUUID();
    UUID explicitActivityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(
        wbsNode(root, projectId, null),
        wbsNode(child, projectId, root),
        wbsNode(grandchild, projectId, child)));

    UUID actRootId = UUID.randomUUID();
    UUID actChildId = UUID.randomUUID();
    UUID actGrandchildId = UUID.randomUUID();
    when(activityRepository.findByWbsNodeId(root)).thenReturn(List.of(activity(actRootId, projectId, root)));
    when(activityRepository.findByWbsNodeId(child)).thenReturn(List.of(activity(actChildId, projectId, child)));
    when(activityRepository.findByWbsNodeId(grandchild)).thenReturn(List.of(activity(actGrandchildId, projectId, grandchild)));

    Activity explicitActivity = activity(explicitActivityId, projectId, UUID.randomUUID());
    when(activityRepository.findAllById(List.of(explicitActivityId))).thenReturn(List.of(explicitActivity));

    ActivityWbsDeletionService service = newService();
    DeleteActivitiesWbsResponse resp = service.deleteActivitiesAndWbs(
        projectId, request(List.of(root), List.of(explicitActivityId), true, false));

    assertThat(resp.aborted()).isFalse();
    assertThat(resp.resolved().wbsNodeIds()).containsExactlyInAnyOrder(root, child, grandchild);
    assertThat(resp.resolved().activityIds())
        .containsExactlyInAnyOrder(actRootId, actChildId, actGrandchildId, explicitActivityId);
    assertThat(resp.willDelete().wbsNodes()).isEqualTo(3);
    assertThat(resp.willDelete().activities()).isEqualTo(4);
  }

  // ---- 2. Wrong-project wbs root / activity id -> abortReason, not included ----

  @Test
  void wrongProjectWbsRootAndActivityId_abortReasonAndExcluded() {
    UUID projectId = UUID.randomUUID();
    UUID foreignWbsId = UUID.randomUUID();
    UUID foreignActivityId = UUID.randomUUID();
    UUID otherProjectId = UUID.randomUUID();

    // The project's own WBS tree does not contain foreignWbsId.
    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of());
    // The activity exists but belongs to a different project.
    when(activityRepository.findAllById(List.of(foreignActivityId)))
        .thenReturn(List.of(activity(foreignActivityId, otherProjectId, UUID.randomUUID())));

    ActivityWbsDeletionService service = newService();
    DeleteActivitiesWbsResponse resp = service.deleteActivitiesAndWbs(
        projectId, request(List.of(foreignWbsId), List.of(foreignActivityId), true, false));

    assertThat(resp.aborted()).isTrue();
    assertThat(resp.abortReasons()).hasSize(2);
    assertThat(resp.resolved().wbsNodeIds()).isEmpty();
    assertThat(resp.resolved().activityIds()).isEmpty();
  }

  // ---- 3. DPR present -> aborted always, even with force; no writes ----

  @Test
  void dprPresent_abortsEvenWithForce_noWrites() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(activityId, projectId, wbsId)));
    when(dailyProgressReportRepository.countDprsByActivityIdInGrouped(List.of(activityId)))
        .thenReturn(List.<Object[]>of(new Object[]{activityId, 3L}));

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withoutForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, false));
    assertThat(withoutForce.aborted()).isTrue();
    assertThat(withoutForce.blockers().activitiesWithDprs()).hasSize(1);
    assertThat(withoutForce.blockers().activitiesWithDprs().get(0).dprCount()).isEqualTo(3L);

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));
    assertThat(withForce.aborted()).isTrue();

    verifyNoWrites();
  }

  // ---- 4. activity_steps count > 0 -> aborted even with force ----

  @Test
  void activityStepsPresent_abortsEvenWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(activityId, projectId, wbsId)));
    when(activityStepRepository.countByActivityIdIn(List.of(activityId))).thenReturn(2L);

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));

    assertThat(withForce.aborted()).isTrue();
    assertThat(withForce.blockers().unexpectedChildData()).anySatisfy(uc -> {
      assertThat(uc.table()).isEqualTo("activity_steps");
      assertThat(uc.count()).isEqualTo(2L);
    });
    verifyNoWrites();
  }

  // ---- 4b. activity_sub_contractor_assignments count > 0 -> aborted even with force ----

  @Test
  void subContractorAssignmentsPresent_abortsEvenWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(activityId, projectId, wbsId)));
    when(activitySubContractorAssignmentRepository.countByActivityIdIn(List.of(activityId))).thenReturn(1L);

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));

    assertThat(withForce.aborted()).isTrue();
    assertThat(withForce.blockers().unexpectedChildData()).anySatisfy(uc -> {
      assertThat(uc.table()).isEqualTo("activity_sub_contractor_assignments");
      assertThat(uc.count()).isEqualTo(1L);
    });
    verifyNoWrites();
  }

  // ---- 4c. risk_activity_assignments count > 0 -> aborted even with force ----

  @Test
  void riskActivityAssignmentsPresent_abortsEvenWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(activityId, projectId, wbsId)));
    when(riskActivityAssignmentRepository.countByActivityIdIn(List.of(activityId))).thenReturn(1L);

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));

    assertThat(withForce.aborted()).isTrue();
    assertThat(withForce.blockers().unexpectedChildData()).anySatisfy(uc -> {
      assertThat(uc.table()).isEqualTo("risk_activity_assignments");
      assertThat(uc.count()).isEqualTo(1L);
    });
    verifyNoWrites();
  }

  // ---- 5. Relationship to a KEPT activity -> aborted without force; deleted with force ----

  @Test
  void relationshipToKeptActivity_abortsWithoutForce_deletedWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID targetActivityId = UUID.randomUUID();
    UUID keptActivityId = UUID.randomUUID();
    UUID relId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(targetActivityId, projectId, wbsId)));
    when(activityRelationshipRepository.findByPredecessorActivityIdIn(List.of(targetActivityId)))
        .thenReturn(List.of(relationship(relId, targetActivityId, keptActivityId)));

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withoutForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, false));
    assertThat(withoutForce.aborted()).isTrue();
    assertThat(withoutForce.blockers().relationshipsToKeptActivities())
        .containsExactly(new DeleteActivitiesWbsResponse.RelationshipToKept(relId, keptActivityId));
    verifyNoWrites();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));
    assertThat(withForce.aborted()).isFalse();
    verify(activityRelationshipRepository).deleteAllById(List.of(relId));
  }

  // ---- 6. BOQ mapped to target WBS -> aborted without force; unlinked with force ----

  @Test
  void boqMappedToTargetWbs_abortsWithoutForce_unlinkedWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID boqItemId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(boqItemRepository.findByWbsNodeIdIn(java.util.Set.of(wbsId)))
        .thenReturn(List.of(boqItem(boqItemId, "1.1", wbsId)));

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withoutForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, false));
    assertThat(withoutForce.aborted()).isTrue();
    assertThat(withoutForce.blockers().boqItemsMappedToDeletedWbs()).hasSize(1);
    verifyNoWrites();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));
    assertThat(withForce.aborted()).isFalse();
    verify(boqItemRepository).nullWbsNodeByWbsNodeIdIn(java.util.Set.of(wbsId));
    verify(wbsService).deleteNode(wbsId);
  }

  // ---- 7. Locked activity -> aborted without force; not aborted with force ----

  @Test
  void lockedActivity_abortsWithoutForce_notAbortedWithForce() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    Activity locked = activity(activityId, projectId, wbsId);
    locked.setEditStatus(ActivityEditStatus.LOCKED);

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(locked));

    ActivityWbsDeletionService service = newService();

    DeleteActivitiesWbsResponse withoutForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, false));
    assertThat(withoutForce.aborted()).isTrue();
    assertThat(withoutForce.blockers().lockedActivities()).containsExactly(activityId);
    verifyNoWrites();

    DeleteActivitiesWbsResponse withForce = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, false, true));
    assertThat(withForce.aborted()).isFalse();
    verify(activityRepository).deleteAllById(List.of(activityId));
    verify(wbsService).deleteNode(wbsId);
  }

  // ---- 8. dryRun=true happy path -> correct counts, ZERO writes ----

  @Test
  void dryRunHappyPath_correctCounts_zeroWrites() {
    UUID projectId = UUID.randomUUID();
    UUID wbsId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(wbsNode(wbsId, projectId, null)));
    when(activityRepository.findByWbsNodeId(wbsId)).thenReturn(List.of(activity(activityId, projectId, wbsId)));

    ActivityWbsDeletionService service = newService();
    DeleteActivitiesWbsResponse resp = service.deleteActivitiesAndWbs(
        projectId, request(List.of(wbsId), null, true, false));

    assertThat(resp.dryRun()).isTrue();
    assertThat(resp.aborted()).isFalse();
    assertThat(resp.resolved().wbsNodeIds()).containsExactly(wbsId);
    assertThat(resp.resolved().activityIds()).containsExactly(activityId);
    assertThat(resp.willDelete().wbsNodes()).isEqualTo(1);
    assertThat(resp.willDelete().activities()).isEqualTo(1);

    verifyNoWrites();
  }

  // ---- 9. Apply happy path -> cascade deletes + deepest-first WBS deletion + audit ----

  @Test
  void applyHappyPath_cascadeDeletesAndDeepestFirstWbsDeletionAndAudit() {
    UUID projectId = UUID.randomUUID();
    UUID root = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    UUID rootActivityId = UUID.randomUUID();
    UUID childActivityId = UUID.randomUUID();

    when(wbsNodeRepository.findByProjectId(projectId)).thenReturn(List.of(
        wbsNode(root, projectId, null),
        wbsNode(child, projectId, root)));
    when(activityRepository.findByWbsNodeId(root)).thenReturn(List.of(activity(rootActivityId, projectId, root)));
    when(activityRepository.findByWbsNodeId(child)).thenReturn(List.of(activity(childActivityId, projectId, child)));

    ActivityWbsDeletionService service = newService();
    DeleteActivitiesWbsResponse resp = service.deleteActivitiesAndWbs(
        projectId, request(List.of(root), null, false, false));

    assertThat(resp.aborted()).isFalse();
    assertThat(resp.dryRun()).isFalse();

    List<UUID> actList = resp.resolved().activityIds();
    verify(resourceAssignmentRepository).deleteByActivityIdIn(actList);
    verify(scheduleActivityResultRepository).deleteByActivityIdIn(actList);
    verify(activityCodeAssignmentRepository).deleteByActivityIdIn(actList);
    verify(activitySupervisorRepository).deleteByActivityIdIn(actList);
    verify(activityRepository).deleteAllById(actList);

    // FK-critical order: all hard-FK child deletes must run before the activity delete itself.
    InOrder ord = inOrder(resourceAssignmentRepository, scheduleActivityResultRepository,
        activityCodeAssignmentRepository, activityRepository);
    ord.verify(resourceAssignmentRepository).deleteByActivityIdIn(any());
    ord.verify(scheduleActivityResultRepository).deleteByActivityIdIn(any());
    ord.verify(activityCodeAssignmentRepository).deleteByActivityIdIn(any());
    ord.verify(activityRepository).deleteAllById(any());

    InOrder inOrder = inOrder(wbsService);
    inOrder.verify(wbsService).deleteNode(child);
    inOrder.verify(wbsService).deleteNode(root);

    // WbsService.deleteNode audits its own WBS-node deletes (mocked here, so not counted); the
    // service itself only audits the 2 deleted activities.
    verify(auditService, times(2)).logDelete(anyString(), any());
  }

  // ---- 10. Empty request -> BusinessRuleException ----

  @Test
  void emptyRequest_throwsBusinessRuleException() {
    ActivityWbsDeletionService service = newService();
    UUID projectId = UUID.randomUUID();

    assertThatThrownBy(() ->
        service.deleteActivitiesAndWbs(projectId, request(null, null, true, false)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Provide at least one");
  }
}
