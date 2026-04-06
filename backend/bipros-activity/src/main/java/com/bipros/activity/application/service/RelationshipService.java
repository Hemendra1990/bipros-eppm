package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.CreateRelationshipRequest;
import com.bipros.activity.application.dto.RelationshipResponse;
import com.bipros.activity.application.dto.UpdateRelationshipRequest;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class RelationshipService {

  private final ActivityRelationshipRepository relationshipRepository;
  private final ActivityRepository activityRepository;

  public RelationshipResponse createRelationship(CreateRelationshipRequest request) {
    log.info("Creating relationship: predecessor={}, successor={}", request.predecessorActivityId(),
        request.successorActivityId());

    // Validate both activities exist
    activityRepository.findById(request.predecessorActivityId())
        .orElseThrow(() -> new ResourceNotFoundException("Activity", request.predecessorActivityId()));
    activityRepository.findById(request.successorActivityId())
        .orElseThrow(() -> new ResourceNotFoundException("Activity", request.successorActivityId()));

    // Prevent self-relationships
    if (request.predecessorActivityId().equals(request.successorActivityId())) {
      throw new BusinessRuleException("SELF_RELATIONSHIP",
          "An activity cannot have a relationship with itself");
    }

    // Check for duplicate relationship
    boolean exists = relationshipRepository.existsByPredecessorActivityIdAndSuccessorActivityId(
        request.predecessorActivityId(), request.successorActivityId());
    if (exists) {
      throw new BusinessRuleException("DUPLICATE_RELATIONSHIP",
          "Relationship already exists between these activities");
    }

    // Detect circular dependency
    if (detectCircularDependency(request.predecessorActivityId(), request.successorActivityId())) {
      throw new BusinessRuleException("CIRCULAR_DEPENDENCY",
          "Creating this relationship would form a circular dependency");
    }

    ActivityRelationship relationship = new ActivityRelationship();
    relationship.setPredecessorActivityId(request.predecessorActivityId());
    relationship.setSuccessorActivityId(request.successorActivityId());
    relationship.setRelationshipType(request.relationshipType() != null
        ? request.relationshipType()
        : RelationshipType.FINISH_TO_START);
    relationship.setLag(request.lag() != null ? request.lag() : 0.0);
    relationship.setIsExternal(false);

    // Get project ID from predecessor activity
    var predecessorActivity = activityRepository.findById(request.predecessorActivityId()).get();
    relationship.setProjectId(predecessorActivity.getProjectId());

    ActivityRelationship saved = relationshipRepository.save(relationship);
    log.info("Relationship created successfully: id={}", saved.getId());
    return RelationshipResponse.from(saved);
  }

  public RelationshipResponse update(UUID projectId, UUID relationshipId, UpdateRelationshipRequest request) {
    log.info("Updating relationship: id={}", relationshipId);

    ActivityRelationship relationship = relationshipRepository.findById(relationshipId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityRelationship", relationshipId));

    relationship.setRelationshipType(RelationshipType.valueOf(request.relationshipType()));
    relationship.setLag(request.lag() != null ? request.lag() : 0.0);

    ActivityRelationship updated = relationshipRepository.save(relationship);
    log.info("Relationship updated successfully: id={}", relationshipId);
    return RelationshipResponse.from(updated);
  }

  public void deleteRelationship(UUID id) {
    log.info("Deleting relationship: id={}", id);

    relationshipRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ActivityRelationship", id));

    relationshipRepository.deleteById(id);
    log.info("Relationship deleted successfully: id={}", id);
  }

  public List<RelationshipResponse> getRelationships(UUID projectId) {
    log.info("Getting relationships for project: projectId={}", projectId);
    return relationshipRepository.findByProjectId(projectId).stream()
        .map(RelationshipResponse::from)
        .toList();
  }

  public List<RelationshipResponse> getPredecessors(UUID activityId) {
    log.info("Getting predecessors for activity: activityId={}", activityId);
    return relationshipRepository.findBySuccessorActivityId(activityId).stream()
        .map(RelationshipResponse::from)
        .toList();
  }

  public List<RelationshipResponse> getSuccessors(UUID activityId) {
    log.info("Getting successors for activity: activityId={}", activityId);
    return relationshipRepository.findByPredecessorActivityId(activityId).stream()
        .map(RelationshipResponse::from)
        .toList();
  }

  /**
   * Detects circular dependency using BFS (Breadth-First Search).
   * Returns true if adding an edge from predecessorId to successorId would create a cycle.
   *
   * @param predecessorId The predecessor activity ID
   * @param successorId The successor activity ID
   * @return true if adding this relationship would create a circular dependency, false otherwise
   */
  boolean detectCircularDependency(UUID predecessorId, UUID successorId) {
    // Walk the successor chain from successorId. If we reach predecessorId, it's circular.
    Set<UUID> visited = new HashSet<>();
    Queue<UUID> queue = new LinkedList<>();
    queue.add(successorId);

    while (!queue.isEmpty()) {
      UUID current = queue.poll();

      if (current.equals(predecessorId)) {
        return true; // Found a path from successor back to predecessor
      }

      if (visited.contains(current)) {
        continue;
      }
      visited.add(current);

      // Add all successors of current to the queue
      relationshipRepository.findByPredecessorActivityId(current).forEach(rel -> {
        queue.add(rel.getSuccessorActivityId());
      });
    }

    return false; // No circular dependency detected
  }
}
