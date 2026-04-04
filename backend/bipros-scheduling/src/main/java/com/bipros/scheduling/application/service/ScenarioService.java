package com.bipros.scheduling.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.scheduling.application.dto.CreateScenarioRequest;
import com.bipros.scheduling.application.dto.ScenarioComparisonResponse;
import com.bipros.scheduling.application.dto.ScenarioComparisonResponse.ActivityChangeDetail;
import com.bipros.scheduling.application.dto.ScheduleScenarioResponse;
import com.bipros.scheduling.domain.model.ScheduleScenario;
import com.bipros.scheduling.domain.model.ScenarioStatus;
import com.bipros.scheduling.domain.repository.ScheduleScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ScenarioService {

  private final ScheduleScenarioRepository scenarioRepository;

  public ScheduleScenarioResponse createScenario(UUID projectId, CreateScenarioRequest request) {
    log.info("Creating scenario for project: {} with name: {}", projectId, request.scenarioName());

    // Check for duplicates
    scenarioRepository.findByProjectIdAndScenarioName(projectId, request.scenarioName())
        .ifPresent(s -> {
          throw new BusinessRuleException("SCENARIO_EXISTS",
              "Scenario with name '" + request.scenarioName() + "' already exists for this project");
        });

    ScheduleScenario scenario = ScheduleScenario.builder()
        .projectId(projectId)
        .scenarioName(request.scenarioName())
        .description(request.description())
        .scenarioType(request.scenarioType())
        .status(ScenarioStatus.DRAFT)
        .build();

    ScheduleScenario saved = scenarioRepository.save(scenario);
    return ScheduleScenarioResponse.from(saved);
  }

  public ScheduleScenarioResponse getScenario(UUID projectId, UUID scenarioId) {
    ScheduleScenario scenario = scenarioRepository.findById(scenarioId)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleScenario", scenarioId.toString()));

    if (!scenario.getProjectId().equals(projectId)) {
      throw new BusinessRuleException("INVALID_PROJECT",
          "Scenario does not belong to the specified project");
    }

    return ScheduleScenarioResponse.from(scenario);
  }

  public List<ScheduleScenarioResponse> listScenarios(UUID projectId) {
    return scenarioRepository.findByProjectId(projectId).stream()
        .map(ScheduleScenarioResponse::from)
        .toList();
  }

  public ScheduleScenarioResponse updateScenario(UUID projectId, UUID scenarioId,
                                                   ScheduleScenario updates) {
    ScheduleScenario scenario = scenarioRepository.findById(scenarioId)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleScenario", scenarioId.toString()));

    if (!scenario.getProjectId().equals(projectId)) {
      throw new BusinessRuleException("INVALID_PROJECT",
          "Scenario does not belong to the specified project");
    }

    if (updates.getScenarioName() != null) {
      scenario.setScenarioName(updates.getScenarioName());
    }
    if (updates.getDescription() != null) {
      scenario.setDescription(updates.getDescription());
    }
    if (updates.getProjectDuration() != null) {
      scenario.setProjectDuration(updates.getProjectDuration());
    }
    if (updates.getCriticalPathLength() != null) {
      scenario.setCriticalPathLength(updates.getCriticalPathLength());
    }
    if (updates.getTotalCost() != null) {
      scenario.setTotalCost(updates.getTotalCost());
    }
    if (updates.getStatus() != null) {
      scenario.setStatus(updates.getStatus());
    }

    ScheduleScenario saved = scenarioRepository.save(scenario);
    return ScheduleScenarioResponse.from(saved);
  }

  public ScenarioComparisonResponse compareScenarios(UUID projectId, UUID scenario1Id,
                                                      UUID scenario2Id) {
    log.info("Comparing scenarios: {} and {} for project: {}", scenario1Id, scenario2Id, projectId);

    ScheduleScenario scenario1 = scenarioRepository.findById(scenario1Id)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleScenario", scenario1Id.toString()));

    ScheduleScenario scenario2 = scenarioRepository.findById(scenario2Id)
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleScenario", scenario2Id.toString()));

    if (!scenario1.getProjectId().equals(projectId) || !scenario2.getProjectId().equals(projectId)) {
      throw new BusinessRuleException("INVALID_PROJECT",
          "One or both scenarios do not belong to the specified project");
    }

    Double durationDiff = (scenario1.getProjectDuration() != null ? scenario1.getProjectDuration() : 0.0)
        - (scenario2.getProjectDuration() != null ? scenario2.getProjectDuration() : 0.0);

    var costDiff = (scenario1.getTotalCost() != null ? scenario1.getTotalCost() : java.math.BigDecimal.ZERO)
        .subtract(scenario2.getTotalCost() != null ? scenario2.getTotalCost() : java.math.BigDecimal.ZERO);

    // Parse modified activities if available
    List<ActivityChangeDetail> changedActivities = new ArrayList<>();
    // This is a simplified implementation; in a real scenario, you'd parse the JSON properly

    return new ScenarioComparisonResponse(
        scenario1.getId(),
        scenario2.getId(),
        scenario1.getScenarioName(),
        scenario2.getScenarioName(),
        scenario1.getProjectDuration(),
        scenario2.getProjectDuration(),
        durationDiff,
        scenario1.getTotalCost(),
        scenario2.getTotalCost(),
        costDiff,
        changedActivities.size(),
        changedActivities
    );
  }
}
