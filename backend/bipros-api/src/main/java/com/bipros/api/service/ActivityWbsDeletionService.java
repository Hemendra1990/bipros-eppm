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
import com.bipros.api.dto.DeleteActivitiesWbsResponse.ActivityDpr;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.Blockers;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.BoqMapped;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.RelationshipToKept;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.Resolved;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.UnexpectedChild;
import com.bipros.api.dto.DeleteActivitiesWbsResponse.WillDelete;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin data-repair: deletes given WBS subtrees (node + all descendants + all activities under
 * them) and/or explicit activity ids, in a safe dry-run-first, guarded, atomic way. See the design
 * spec ({@code docs/superpowers/specs/2026-07-04-delete-activities-wbs-endpoint-design.md}) §4-§7
 * for the cascade facts, contract and algorithm this implements.
 *
 * <p>{@code dryRun} (request default true) and an aborted run (a blocking condition found) BOTH
 * perform ZERO writes — only reads/counts run. DPRs / activity_steps / sub-contractor assignments /
 * risk assignments always abort, even under {@code force}; {@code force} overrides only
 * relationships-to-a-kept-activity, BOQ-mapped-to-deleted-WBS, and locked activities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityWbsDeletionService {

  private final WbsNodeRepository wbsNodeRepository;
  private final ActivityRepository activityRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final ScheduleActivityResultRepository scheduleActivityResultRepository;
  private final ActivityCodeAssignmentRepository activityCodeAssignmentRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final ActivitySupervisorRepository activitySupervisorRepository;
  private final DailyProgressReportRepository dailyProgressReportRepository;
  private final ActivityStepRepository activityStepRepository;
  private final ActivitySubContractorAssignmentRepository activitySubContractorAssignmentRepository;
  private final RiskActivityAssignmentRepository riskActivityAssignmentRepository;
  private final BoqItemRepository boqItemRepository;
  private final WbsService wbsService;
  private final AuditService auditService;

  // Not in the Lombok constructor: non-final, so the pure-Mockito unit test can construct the
  // service without a Spring context (field stays null there). In production Spring field-injects
  // it. Mirrors ProjectDataRepairService's EntityManager pattern.
  @PersistenceContext
  private EntityManager entityManager;

  @Transactional
  public DeleteActivitiesWbsResponse deleteActivitiesAndWbs(UUID projectId, DeleteActivitiesWbsRequest req) {
    boolean wbsEmpty = req.getWbsNodeIds() == null || req.getWbsNodeIds().isEmpty();
    boolean actEmpty = req.getActivityIds() == null || req.getActivityIds().isEmpty();
    if (wbsEmpty && actEmpty) {
      throw new BusinessRuleException("NOTHING_TO_DELETE",
          "Provide at least one wbsNodeId or activityId to delete");
    }

    boolean dry = req.isDryRun();
    boolean force = req.isForce();
    List<String> abortReasons = new ArrayList<>();

    // ---- Resolve WBS subtree ----
    List<WbsNode> allNodes = wbsNodeRepository.findByProjectId(projectId);
    Map<UUID, WbsNode> byId = allNodes.stream()
        .collect(Collectors.toMap(WbsNode::getId, n -> n, (a, b) -> a));
    Map<UUID, List<WbsNode>> childrenByParent = allNodes.stream()
        .filter(n -> n.getParentId() != null)
        .collect(Collectors.groupingBy(WbsNode::getParentId));

    Set<UUID> targetWbs = new LinkedHashSet<>();
    if (req.getWbsNodeIds() != null) {
      for (UUID rootId : req.getWbsNodeIds()) {
        if (!byId.containsKey(rootId)) {
          abortReasons.add("WBS node " + rootId + " not found in project " + projectId);
          continue;
        }
        collectSubtree(rootId, childrenByParent, targetWbs);
      }
    }

    // ---- Resolve activities ----
    Map<UUID, Activity> actById = new LinkedHashMap<>();
    for (UUID w : targetWbs) {
      for (Activity a : activityRepository.findByWbsNodeId(w)) {
        actById.put(a.getId(), a);
      }
    }
    if (req.getActivityIds() != null && !req.getActivityIds().isEmpty()) {
      Map<UUID, Activity> foundById = activityRepository.findAllById(req.getActivityIds()).stream()
          .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));
      for (UUID id : req.getActivityIds()) {
        Activity a = foundById.get(id);
        if (a == null || !projectId.equals(a.getProjectId())) {
          abortReasons.add("Activity " + id + " not found in project " + projectId);
          continue;
        }
        actById.put(a.getId(), a);
      }
    }
    List<UUID> actList = new ArrayList<>(actById.keySet());
    Set<UUID> targetActs = actById.keySet();

    // ---- Tripwires / blockers (all reads) ----

    // DPRs: ALWAYS abort, even with force.
    List<ActivityDpr> activitiesWithDprs = new ArrayList<>();
    if (!actList.isEmpty()) {
      for (Object[] row : dailyProgressReportRepository.countDprsByActivityIdInGrouped(actList)) {
        UUID activityId = (UUID) row[0];
        long count = ((Number) row[1]).longValue();
        Activity a = actById.get(activityId);
        activitiesWithDprs.add(new ActivityDpr(activityId, a == null ? null : a.getCode(), count));
      }
    }
    if (!activitiesWithDprs.isEmpty()) {
      abortReasons.add(activitiesWithDprs.size() + " activities have DPRs");
    }

    // Should-be-zero child tables: ALWAYS abort, even with force.
    List<UnexpectedChild> unexpectedChildData = new ArrayList<>();
    if (!actList.isEmpty()) {
      long stepsCount = activityStepRepository.countByActivityIdIn(actList);
      if (stepsCount > 0) {
        unexpectedChildData.add(new UnexpectedChild(null, "activity_steps", stepsCount));
        abortReasons.add(stepsCount + " activity_steps rows exist for target activities");
      }
      long subContractorCount = activitySubContractorAssignmentRepository.countByActivityIdIn(actList);
      if (subContractorCount > 0) {
        unexpectedChildData.add(new UnexpectedChild(null, "activity_sub_contractor_assignments", subContractorCount));
        abortReasons.add(subContractorCount + " activity_sub_contractor_assignments rows exist for target activities");
      }
      long riskCount = riskActivityAssignmentRepository.countByActivityIdIn(actList);
      if (riskCount > 0) {
        unexpectedChildData.add(new UnexpectedChild(null, "risk_activity_assignments", riskCount));
        abortReasons.add(riskCount + " risk_activity_assignments rows exist for target activities");
      }
    }

    // Relationships to a KEPT activity: abort unless force.
    Map<UUID, ActivityRelationship> relById = new LinkedHashMap<>();
    if (!actList.isEmpty()) {
      for (ActivityRelationship r : activityRelationshipRepository.findByPredecessorActivityIdIn(actList)) {
        relById.put(r.getId(), r);
      }
      for (ActivityRelationship r : activityRelationshipRepository.findBySuccessorActivityIdIn(actList)) {
        relById.put(r.getId(), r);
      }
    }
    List<UUID> intraTargetRelIds = new ArrayList<>();
    List<RelationshipToKept> relationshipsToKept = new ArrayList<>();
    List<UUID> toKeptRelIds = new ArrayList<>();
    for (ActivityRelationship r : relById.values()) {
      boolean predIn = targetActs.contains(r.getPredecessorActivityId());
      boolean succIn = targetActs.contains(r.getSuccessorActivityId());
      if (predIn && succIn) {
        intraTargetRelIds.add(r.getId());
      } else if (predIn || succIn) {
        UUID keptId = predIn ? r.getSuccessorActivityId() : r.getPredecessorActivityId();
        relationshipsToKept.add(new RelationshipToKept(r.getId(), keptId));
        toKeptRelIds.add(r.getId());
      }
    }
    if (!relationshipsToKept.isEmpty() && !force) {
      abortReasons.add(relationshipsToKept.size() + " relationships to kept activities (use force to delete)");
    }

    // BOQ items mapped to a targeted WBS node: abort unless force.
    List<BoqMapped> boqItemsMappedToDeletedWbs = new ArrayList<>();
    if (!targetWbs.isEmpty()) {
      for (BoqItem b : boqItemRepository.findByWbsNodeIdIn(targetWbs)) {
        boqItemsMappedToDeletedWbs.add(new BoqMapped(b.getId(), b.getItemNo(), b.getWbsNodeId()));
      }
    }
    if (!boqItemsMappedToDeletedWbs.isEmpty() && !force) {
      abortReasons.add(boqItemsMappedToDeletedWbs.size()
          + " BOQ items mapped to targeted WBS nodes (use force to unlink)");
    }

    // Locked activities: abort unless force.
    List<UUID> lockedActivities = actById.values().stream()
        .filter(a -> a.getEditStatus() == ActivityEditStatus.LOCKED)
        .map(Activity::getId)
        .toList();
    if (!lockedActivities.isEmpty() && !force) {
      abortReasons.add(lockedActivities.size() + " locked activities (use force to delete)");
    }

    boolean aborted = !abortReasons.isEmpty();

    // ---- willDelete counts ----
    long resourceAssignments = actList.isEmpty() ? 0 : resourceAssignmentRepository.countByActivityIdIn(actList);
    long scheduleResults = actList.isEmpty() ? 0 : scheduleActivityResultRepository.countByActivityIdIn(actList);
    long codeAssignments = actList.isEmpty() ? 0 : activityCodeAssignmentRepository.countByActivityIdIn(actList);
    long supervisors = actList.isEmpty() ? 0 : activitySupervisorRepository.findByActivityIdIn(actList).size();

    WillDelete willDelete = new WillDelete(
        targetWbs.size(),
        targetActs.size(),
        (int) resourceAssignments,
        (int) scheduleResults,
        (int) codeAssignments,
        intraTargetRelIds.size(),
        (int) supervisors);

    // ---- Apply (only when !dry && !aborted); NONE of these run otherwise ----
    if (!dry && !aborted) {
      if (!actList.isEmpty()) {
        resourceAssignmentRepository.deleteByActivityIdIn(actList);
        scheduleActivityResultRepository.deleteByActivityIdIn(actList);
        activityCodeAssignmentRepository.deleteByActivityIdIn(actList);

        List<UUID> relIdsToDelete = new ArrayList<>(intraTargetRelIds);
        if (force) {
          relIdsToDelete.addAll(toKeptRelIds);
        }
        if (!relIdsToDelete.isEmpty()) {
          activityRelationshipRepository.deleteAllById(relIdsToDelete);
        }

        activitySupervisorRepository.deleteByActivityIdIn(actList);
        activityRepository.deleteAllById(actList);

        for (UUID id : actList) {
          auditService.logDelete("Activity", id);
        }
      }

      if (force && !boqItemsMappedToDeletedWbs.isEmpty()) {
        boqItemRepository.nullWbsNodeByWbsNodeIdIn(targetWbs);
      }

      // Explicit flush: the activities -> wbs_nodes FK is RESTRICT, so every activity (and
      // child-row) delete above must physically hit the DB before any wbs_nodes delete below.
      // Guarded because the pure-Mockito unit test leaves entityManager null.
      if (entityManager != null) {
        entityManager.flush();
      }

      // Delete WBS nodes deepest-first so WbsService.deleteNode's own child/activity guards
      // (now that children/activities are gone) act as the final net.
      List<UUID> orderedWbs = new ArrayList<>(targetWbs);
      Map<UUID, Integer> depthById = new HashMap<>();
      for (UUID id : orderedWbs) {
        depthById.put(id, depthOf(id, byId));
      }
      orderedWbs.sort(Comparator.comparingInt(depthById::get).reversed());
      for (UUID id : orderedWbs) {
        wbsService.deleteNode(id);
      }
    }

    return new DeleteActivitiesWbsResponse(
        dry,
        aborted,
        abortReasons,
        new Resolved(new ArrayList<>(targetWbs), actList),
        willDelete,
        new Blockers(activitiesWithDprs, relationshipsToKept, boqItemsMappedToDeletedWbs,
            lockedActivities, unexpectedChildData));
  }

  private static void collectSubtree(UUID nodeId, Map<UUID, List<WbsNode>> childrenByParent, Set<UUID> out) {
    // out.add returns false if already visited — guards against a cyclic parentId graph (this is a
    // data-repair tool for possibly-corrupted imports where parent_id is an unenforced raw UUID).
    if (!out.add(nodeId)) return;
    for (WbsNode child : childrenByParent.getOrDefault(nodeId, List.of())) {
      collectSubtree(child.getId(), childrenByParent, out);
    }
  }

  /** Number of ancestors above {@code id} (root = 0), walking {@code parentId} up via {@code byId}. */
  private static int depthOf(UUID id, Map<UUID, WbsNode> byId) {
    int depth = 0;
    UUID current = id;
    Set<UUID> seen = new HashSet<>();
    while (current != null && seen.add(current)) {
      WbsNode node = byId.get(current);
      UUID parentId = node == null ? null : node.getParentId();
      if (parentId == null) {
        break;
      }
      depth++;
      current = parentId;
    }
    return depth;
  }
}
