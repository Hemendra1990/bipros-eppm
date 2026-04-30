package com.bipros.resource.application.service;

import com.bipros.common.event.LabourReturnSubmittedEvent;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateLabourReturnRequest;
import com.bipros.resource.application.dto.DeploymentSummary;
import com.bipros.resource.application.dto.LabourReturnResponse;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.SkillCategory;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class LabourReturnService {

  private final LabourReturnRepository labourReturnRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public LabourReturnResponse createReturn(CreateLabourReturnRequest request) {
    log.info(
        "Creating labour return: projectId={}, contractorName={}, returnDate={}",
        request.projectId(),
        request.contractorName(),
        request.returnDate());

    LabourReturn labourReturn =
        LabourReturn.builder()
            .projectId(request.projectId())
            .contractorName(request.contractorName())
            .returnDate(request.returnDate())
            .skillCategory(request.skillCategory())
            .headCount(request.headCount())
            .manDays(request.manDays())
            .wbsNodeId(request.wbsNodeId())
            .siteLocation(request.siteLocation())
            .remarks(request.remarks())
            .build();

    LabourReturn saved = labourReturnRepository.save(labourReturn);
    log.info("Labour return created: id={}", saved.getId());

    auditService.logCreate("LabourReturn", saved.getId(), LabourReturnResponse.from(saved));
    eventPublisher.publishEvent(new LabourReturnSubmittedEvent(
        saved.getProjectId(), saved.getId(), saved.getReturnDate()));

    return LabourReturnResponse.from(saved);
  }

  public Page<LabourReturnResponse> getReturnsByProject(
      UUID projectId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
    log.info(
        "Fetching labour returns for project: projectId={}, fromDate={}, toDate={}",
        projectId,
        fromDate,
        toDate);
    return labourReturnRepository
        .findByProjectIdAndReturnDateBetween(projectId, fromDate, toDate, pageable)
        .map(LabourReturnResponse::from);
  }

  public List<DeploymentSummary> getDeploymentSummary(UUID projectId) {
    log.info("Computing deployment summary for project: projectId={}", projectId);

    LocalDate now = LocalDate.now();
    LocalDate thirtyDaysAgo = now.minusDays(30);

    List<Object[]> results =
        labourReturnRepository.getSkillCategorySummary(projectId, thirtyDaysAgo, now);

    List<DeploymentSummary> summaries = new ArrayList<>();
    for (Object[] row : results) {
      SkillCategory category = (SkillCategory) row[0];
      Integer headCount = ((Number) row[1]).intValue();
      Double manDays = ((Number) row[2]).doubleValue();

      summaries.add(new DeploymentSummary(category, headCount, manDays));
    }

    return summaries;
  }

  public Integer getTotalHeadCountByDate(UUID projectId, LocalDate date) {
    log.info("Getting total headcount for projectId={}, date={}", projectId, date);
    Integer total = labourReturnRepository.getTotalHeadCountByDateAndProject(projectId, date);
    return total != null ? total : 0;
  }
}
