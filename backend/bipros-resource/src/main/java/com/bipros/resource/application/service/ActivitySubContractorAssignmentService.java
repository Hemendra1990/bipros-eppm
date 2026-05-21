package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ActivitySubContractorAssignmentResponse;
import com.bipros.resource.application.dto.CreateActivitySubContractorAssignmentRequest;
import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import com.bipros.resource.domain.model.master.SubContractorMaster;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.SubContractorMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivitySubContractorAssignmentService {

  private final ActivitySubContractorAssignmentRepository assignmentRepository;
  private final SubContractorMasterRepository masterRepository;
  private final ActivityRepository activityRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ActivitySubContractorAssignmentResponse> listForActivity(
      UUID projectId, UUID activityId) {
    List<ActivitySubContractorAssignment> rows =
        assignmentRepository.findByProjectIdAndActivityId(projectId, activityId);
    if (rows.isEmpty()) return List.of();

    var masterIds = rows.stream()
        .map(ActivitySubContractorAssignment::getSubContractorMasterId)
        .distinct()
        .toList();
    Map<UUID, SubContractorMaster> masterMap = masterRepository.findAllById(masterIds).stream()
        .collect(Collectors.toMap(SubContractorMaster::getId, m -> m));

    return rows.stream()
        .map(r -> ActivitySubContractorAssignmentResponse.from(
            r, masterMap.get(r.getSubContractorMasterId())))
        .toList();
  }

  public ActivitySubContractorAssignmentResponse create(
      UUID projectId, CreateActivitySubContractorAssignmentRequest req) {
    UUID activityId = UUID.fromString(req.activityId());
    UUID masterId = UUID.fromString(req.subContractorMasterId());

    assertActivityEditable(activityId);

    SubContractorMaster master = masterRepository.findById(masterId)
        .orElseThrow(() -> new ResourceNotFoundException("SubContractorMaster", masterId));

    ActivitySubContractorAssignment assignment = ActivitySubContractorAssignment.builder()
        .activityId(activityId)
        .projectId(projectId)
        .subContractorMasterId(masterId)
        .build();

    ActivitySubContractorAssignment saved = assignmentRepository.save(assignment);
    log.info("Sub-contractor assignment created: id={}, activity={}, master={}",
        saved.getId(), activityId, masterId);

    ActivitySubContractorAssignmentResponse response =
        ActivitySubContractorAssignmentResponse.from(saved, master);
    auditService.logCreate("ActivitySubContractorAssignment", saved.getId(), response);
    return response;
  }

  public void delete(UUID assignmentId) {
    ActivitySubContractorAssignment a = assignmentRepository.findById(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("ActivitySubContractorAssignment", assignmentId));
    assertActivityEditable(a.getActivityId());
    assignmentRepository.delete(a);
    auditService.logDelete("ActivitySubContractorAssignment", assignmentId);
  }

  private void assertActivityEditable(UUID activityId) {
    if (activityId == null) return;
    Activity activity = activityRepository.findById(activityId).orElse(null);
    if (activity == null) return;
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      throw new BusinessRuleException(
          "ACTIVITY_LOCKED",
          "Activity '" + activity.getCode() + "' is locked. Unlock it before editing.");
    }
  }
}
