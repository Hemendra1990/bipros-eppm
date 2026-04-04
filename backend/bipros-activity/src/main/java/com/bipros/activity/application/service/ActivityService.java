package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.UpdateActivityRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository relationshipRepository;

  public ActivityResponse createActivity(CreateActivityRequest request) {
    log.info("Creating activity: code={}, name={}, projectId={}", request.code(), request.name(),
        request.projectId());

    Activity activity = new Activity();
    activity.setCode(request.code());
    activity.setName(request.name());
    activity.setDescription(request.description());
    activity.setProjectId(request.projectId());
    activity.setWbsNodeId(request.wbsNodeId());

    if (request.activityType() != null) {
      activity.setActivityType(request.activityType());
    }
    if (request.durationType() != null) {
      activity.setDurationType(request.durationType());
    }
    if (request.percentCompleteType() != null) {
      activity.setPercentCompleteType(request.percentCompleteType());
    }

    activity.setOriginalDuration(request.originalDuration());
    activity.setPlannedStartDate(request.plannedStartDate());
    activity.setPlannedFinishDate(request.plannedFinishDate());
    activity.setCalendarId(request.calendarId());
    activity.setPercentComplete(0.0);

    Activity saved = activityRepository.save(activity);
    log.info("Activity created successfully: id={}", saved.getId());
    return ActivityResponse.from(saved);
  }

  public ActivityResponse updateActivity(UUID id, UpdateActivityRequest request) {
    log.info("Updating activity: id={}", id);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    activity.setName(request.name());
    activity.setDescription(request.description());

    if (request.wbsNodeId() != null) {
      activity.setWbsNodeId(request.wbsNodeId());
    }
    if (request.activityType() != null) {
      activity.setActivityType(request.activityType());
    }
    if (request.durationType() != null) {
      activity.setDurationType(request.durationType());
    }
    if (request.percentCompleteType() != null) {
      activity.setPercentCompleteType(request.percentCompleteType());
    }
    if (request.status() != null) {
      activity.setStatus(request.status());
    }

    activity.setOriginalDuration(request.originalDuration());
    activity.setRemainingDuration(request.remainingDuration());
    activity.setPercentComplete(request.percentComplete());
    activity.setPhysicalPercentComplete(request.physicalPercentComplete());
    activity.setActualStartDate(request.actualStartDate());
    activity.setActualFinishDate(request.actualFinishDate());
    activity.setCalendarId(request.calendarId());
    activity.setPrimaryConstraintType(request.primaryConstraintType());
    activity.setPrimaryConstraintDate(request.primaryConstraintDate());
    activity.setSecondaryConstraintType(request.secondaryConstraintType());
    activity.setSecondaryConstraintDate(request.secondaryConstraintDate());
    activity.setSuspendDate(request.suspendDate());
    activity.setResumeDate(request.resumeDate());

    Activity updated = activityRepository.save(activity);
    log.info("Activity updated successfully: id={}", id);
    return ActivityResponse.from(updated);
  }

  public void deleteActivity(UUID id) {
    log.info("Deleting activity: id={}", id);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    boolean hasRelationships = !relationshipRepository.findByPredecessorActivityId(id).isEmpty()
        || !relationshipRepository.findBySuccessorActivityId(id).isEmpty();

    if (hasRelationships) {
      throw new BusinessRuleException("ACTIVITY_HAS_RELATIONSHIPS",
          "Cannot delete activity with relationships. Remove relationships first.");
    }

    activityRepository.deleteById(id);
    log.info("Activity deleted successfully: id={}", id);
  }

  public ActivityResponse getActivity(UUID id) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    return ActivityResponse.from(activity);
  }

  public PagedResponse<ActivityResponse> listActivities(UUID projectId, Pageable pageable) {
    log.info("Listing activities for project: projectId={}, page={}, size={}", projectId,
        pageable.getPageNumber(), pageable.getPageSize());

    Page<Activity> page = activityRepository.findByProjectIdOrderBySortOrder(projectId, pageable);
    return PagedResponse.of(
        page.getContent().stream().map(ActivityResponse::from).toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize()
    );
  }

  public java.util.List<ActivityResponse> getActivitiesByWbs(UUID wbsNodeId) {
    log.info("Getting activities for WBS node: wbsNodeId={}", wbsNodeId);
    return activityRepository.findByWbsNodeId(wbsNodeId).stream()
        .map(ActivityResponse::from)
        .toList();
  }

  public ActivityResponse updateProgress(UUID id, Double percentComplete, LocalDate actualStart,
      LocalDate actualFinish) {
    log.info("Updating progress for activity: id={}, percentComplete={}", id, percentComplete);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    if (percentComplete < 0 || percentComplete > 100) {
      throw new BusinessRuleException("INVALID_PERCENT_COMPLETE",
          "Percent complete must be between 0 and 100");
    }

    activity.setPercentComplete(percentComplete);
    activity.setActualStartDate(actualStart);
    activity.setActualFinishDate(actualFinish);

    Activity updated = activityRepository.save(activity);
    log.info("Progress updated successfully: id={}", id);
    return ActivityResponse.from(updated);
  }
}
