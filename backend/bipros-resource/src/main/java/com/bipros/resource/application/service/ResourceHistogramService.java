package com.bipros.resource.application.service;

import com.bipros.resource.application.dto.ResourceHistogramEntry;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ResourceHistogramService {

  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final EquipmentLogRepository equipmentLogRepository;
  private final LabourReturnRepository labourReturnRepository;

  public List<ResourceHistogramEntry> getHistogram(
      UUID projectId, UUID resourceId, LocalDate fromDate, LocalDate toDate) {
    log.info(
        "Computing resource histogram: projectId={}, resourceId={}, fromDate={}, toDate={}",
        projectId,
        resourceId,
        fromDate,
        toDate);

    Map<String, Double> plannedMap = new HashMap<>();
    Map<String, Double> actualMap = new HashMap<>();

    // Collect all months in range
    YearMonth current = YearMonth.from(fromDate);
    YearMonth end = YearMonth.from(toDate);
    while (!current.isAfter(end)) {
      String monthKey = current.toString();
      plannedMap.put(monthKey, 0.0);
      actualMap.put(monthKey, 0.0);
      current = current.plusMonths(1);
    }

    // Get planned data from ResourceAssignment
    List<ResourceAssignment> assignments =
        resourceAssignmentRepository.findByResourceIdAndPlannedStartDateBetween(
            resourceId, fromDate, toDate);

    for (ResourceAssignment assignment : assignments) {
      if (assignment.getPlannedUnits() != null && assignment.getPlannedStartDate() != null) {
        YearMonth month = YearMonth.from(assignment.getPlannedStartDate());
        String monthKey = month.toString();
        if (plannedMap.containsKey(monthKey)) {
          plannedMap.put(
              monthKey, plannedMap.get(monthKey) + assignment.getPlannedUnits());
        }
      }
    }

    // Get actual data from EquipmentLog (for equipment resources)
    List<EquipmentLog> equipmentLogs =
        equipmentLogRepository.findByProjectIdAndLogDateBetween(projectId, fromDate, toDate);

    for (EquipmentLog log : equipmentLogs) {
      if (log.getResourceId().equals(resourceId) && log.getOperatingHours() != null) {
        YearMonth month = YearMonth.from(log.getLogDate());
        String monthKey = month.toString();
        if (actualMap.containsKey(monthKey)) {
          actualMap.put(monthKey, actualMap.get(monthKey) + log.getOperatingHours());
        }
      }
    }

    // Get actual data from LabourReturn (for labour resources)
    List<LabourReturn> labourReturns =
        labourReturnRepository.findByProjectIdAndReturnDateBetween(projectId, fromDate, toDate);

    for (LabourReturn return_ : labourReturns) {
      if (return_.getManDays() != null) {
        YearMonth month = YearMonth.from(return_.getReturnDate());
        String monthKey = month.toString();
        if (actualMap.containsKey(monthKey)) {
          actualMap.put(monthKey, actualMap.get(monthKey) + return_.getManDays());
        }
      }
    }

    // Build result
    List<ResourceHistogramEntry> result = new ArrayList<>();
    plannedMap.forEach(
        (monthKey, planned) -> {
          Double actual = actualMap.getOrDefault(monthKey, 0.0);
          result.add(new ResourceHistogramEntry(monthKey, planned, actual));
        });

    result.sort((a, b) -> a.period().compareTo(b.period()));
    return result;
  }
}
