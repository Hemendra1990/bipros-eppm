package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateEquipmentLogRequest;
import com.bipros.resource.application.dto.EquipmentLogResponse;
import com.bipros.resource.application.dto.EquipmentUtilizationSummary;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.EquipmentStatus;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class EquipmentLogService {

  private final EquipmentLogRepository equipmentLogRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  public EquipmentLogResponse createLog(CreateEquipmentLogRequest request) {
    log.info(
        "Creating equipment log: resourceId={}, projectId={}, logDate={}",
        request.resourceId(),
        request.projectId(),
        request.logDate());

    resourceRepository
        .findById(request.resourceId())
        .orElseThrow(() -> new ResourceNotFoundException("Resource", request.resourceId()));

    EquipmentLog logEntry =
        EquipmentLog.builder()
            .resourceId(request.resourceId())
            .projectId(request.projectId())
            .logDate(request.logDate())
            .deploymentSite(request.deploymentSite())
            .operatingHours(request.operatingHours() != null ? request.operatingHours() : 0.0)
            .idleHours(request.idleHours() != null ? request.idleHours() : 0.0)
            .breakdownHours(request.breakdownHours() != null ? request.breakdownHours() : 0.0)
            .fuelConsumed(request.fuelConsumed())
            .operatorName(request.operatorName())
            .remarks(request.remarks())
            .status(request.status() != null ? request.status() : EquipmentStatus.WORKING)
            .build();

    EquipmentLog saved = equipmentLogRepository.save(logEntry);
    log.info("Equipment log created: id={}", saved.getId());

    auditService.logCreate("EquipmentLog", saved.getId(), EquipmentLogResponse.from(saved));

    return EquipmentLogResponse.from(saved);
  }

  public Page<EquipmentLogResponse> getLogsByResource(
      UUID resourceId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
    log.info(
        "Fetching equipment logs for resource: resourceId={}, fromDate={}, toDate={}",
        resourceId,
        fromDate,
        toDate);
    return equipmentLogRepository
        .findByResourceIdAndLogDateBetween(resourceId, fromDate, toDate, pageable)
        .map(EquipmentLogResponse::from);
  }

  public Page<EquipmentLogResponse> getLogsByProject(
      UUID projectId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
    log.info(
        "Fetching equipment logs for project: projectId={}, fromDate={}, toDate={}",
        projectId,
        fromDate,
        toDate);
    return equipmentLogRepository
        .findByProjectIdAndLogDateBetween(projectId, fromDate, toDate, pageable)
        .map(EquipmentLogResponse::from);
  }

  public List<EquipmentUtilizationSummary> getUtilizationSummary(UUID projectId) {
    log.info("Computing utilization summary for project: projectId={}", projectId);

    LocalDate now = LocalDate.now();
    LocalDate thirtyDaysAgo = now.minusDays(30);

    List<EquipmentLog> logs =
        equipmentLogRepository.findByProjectIdAndLogDateBetween(
            projectId, thirtyDaysAgo, now);

    Map<UUID, EquipmentUtilizationSummary> summaryMap = new HashMap<>();

    for (EquipmentLog log : logs) {
      summaryMap
          .computeIfAbsent(
              log.getResourceId(),
              rid -> {
                var resource = resourceRepository.findById(rid).orElse(null);
                return new EquipmentUtilizationSummary(
                    rid,
                    resource != null ? resource.getName() : "Unknown",
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0);
              });
    }

    for (EquipmentLog log : logs) {
      var resourceId = log.getResourceId();
      var existing = summaryMap.get(resourceId);
      double operating = existing.totalOperatingHours() + (log.getOperatingHours() != null ? log.getOperatingHours() : 0.0);
      double idle = existing.totalIdleHours() + (log.getIdleHours() != null ? log.getIdleHours() : 0.0);
      double breakdown = existing.totalBreakdownHours() + (log.getBreakdownHours() != null ? log.getBreakdownHours() : 0.0);
      double available = operating + idle + breakdown;
      double utilization = available > 0 ? (operating / available) * 100 : 0.0;

      summaryMap.put(
          resourceId,
          new EquipmentUtilizationSummary(
              existing.resourceId(),
              existing.resourceName(),
              operating,
              idle,
              breakdown,
              utilization,
              available));
    }

    return new ArrayList<>(summaryMap.values());
  }
}
