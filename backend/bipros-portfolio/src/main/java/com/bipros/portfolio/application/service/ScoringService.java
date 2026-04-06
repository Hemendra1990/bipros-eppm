package com.bipros.portfolio.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.portfolio.application.dto.AddScoringCriterionRequest;
import com.bipros.portfolio.application.dto.CreateScoringModelRequest;
import com.bipros.portfolio.application.dto.ProjectRankingResponse;
import com.bipros.portfolio.application.dto.ScoringCriterionResponse;
import com.bipros.portfolio.application.dto.ScoringModelResponse;
import com.bipros.portfolio.domain.ProjectScore;
import com.bipros.portfolio.domain.ScoringCriterion;
import com.bipros.portfolio.domain.ScoringModel;
import com.bipros.portfolio.infrastructure.repository.ProjectScoreRepository;
import com.bipros.portfolio.infrastructure.repository.ScoringCriterionRepository;
import com.bipros.portfolio.infrastructure.repository.ScoringModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoringService {

  private final ScoringModelRepository scoringModelRepository;
  private final ScoringCriterionRepository scoringCriterionRepository;
  private final ProjectScoreRepository projectScoreRepository;
  private final AuditService auditService;

  public ScoringModelResponse createScoringModel(CreateScoringModelRequest request) {
    ScoringModel model = new ScoringModel();
    model.setName(request.name());
    model.setDescription(request.description());
    model.setIsDefault(false);

    ScoringModel saved = scoringModelRepository.save(model);
    auditService.logCreate("ScoringModel", saved.getId(), saved);
    return ScoringModelResponse.from(saved);
  }

  public ScoringModelResponse getScoringModel(UUID modelId) {
    ScoringModel model =
        scoringModelRepository
            .findById(modelId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ScoringModel", modelId));
    return ScoringModelResponse.from(model);
  }

  public List<ScoringModelResponse> listScoringModels() {
    return scoringModelRepository.findAll().stream()
        .map(ScoringModelResponse::from)
        .toList();
  }

  @Transactional
  public ScoringCriterionResponse addCriterion(
      UUID modelId, AddScoringCriterionRequest request) {
    ScoringModel model =
        scoringModelRepository
            .findById(modelId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ScoringModel", modelId));

    ScoringCriterion criterion = new ScoringCriterion();
    criterion.setScoringModelId(modelId);
    criterion.setName(request.name());
    criterion.setWeight(request.weight());
    criterion.setMinScore(request.minScore() != null ? request.minScore() : 0.0);
    criterion.setMaxScore(request.maxScore() != null ? request.maxScore() : 10.0);
    criterion.setSortOrder(request.sortOrder());

    ScoringCriterion saved = scoringCriterionRepository.save(criterion);
    auditService.logCreate("ScoringCriterion", saved.getId(), saved);
    return ScoringCriterionResponse.from(saved);
  }

  public List<ScoringCriterionResponse> getModelCriteria(UUID modelId) {
    return scoringCriterionRepository.findByScoringModelIdOrderBySortOrder(modelId).stream()
        .map(ScoringCriterionResponse::from)
        .toList();
  }

  @Transactional
  public void scoreProject(UUID projectId, UUID modelId, UUID criterionId, Double score) {
    ScoringModel model =
        scoringModelRepository
            .findById(modelId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ScoringModel", modelId));

    ScoringCriterion criterion =
        scoringCriterionRepository
            .findById(criterionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ScoringCriterion", criterionId));

    ProjectScore projectScore =
        projectScoreRepository
            .findByProjectIdAndScoringCriterionId(projectId, criterionId)
            .orElse(new ProjectScore());

    Double oldScore = projectScore.getScore();
    projectScore.setProjectId(projectId);
    projectScore.setScoringModelId(modelId);
    projectScore.setScoringCriterionId(criterionId);
    projectScore.setScore(score);

    ProjectScore saved = projectScoreRepository.save(projectScore);
    if (oldScore == null) {
      auditService.logCreate("ProjectScore", saved.getId(), saved);
    } else {
      auditService.logUpdate("ProjectScore", saved.getId(), "score", oldScore, score);
    }
  }

  public Double calculateWeightedScore(UUID projectId, UUID modelId) {
    List<ProjectScore> scores =
        projectScoreRepository.findByProjectIdAndScoringModelId(projectId, modelId);

    if (scores.isEmpty()) {
      return 0.0;
    }

    double weightedSum = 0.0;
    double totalWeight = 0.0;

    for (ProjectScore ps : scores) {
      ScoringCriterion criterion =
          scoringCriterionRepository
              .findById(ps.getScoringCriterionId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "ScoringCriterion", ps.getScoringCriterionId()));
      weightedSum += ps.getScore() * (criterion.getWeight() / 100.0);
      totalWeight += criterion.getWeight();
    }

    return totalWeight > 0 ? weightedSum : 0.0;
  }

  public List<ProjectRankingResponse> prioritizeProjects(UUID modelId) {
    List<ProjectScore> allScores = projectScoreRepository.findAll();
    List<ProjectScore> modelScores =
        allScores.stream()
            .filter(ps -> ps.getScoringModelId().equals(modelId))
            .toList();

    // Group by project and calculate weighted score
    java.util.Map<UUID, Double> projectScores = new java.util.HashMap<>();

    for (ProjectScore ps : modelScores) {
      projectScores.computeIfAbsent(ps.getProjectId(), k -> calculateWeightedScore(k, modelId));
    }

    // Convert to responses and sort by score
    List<ProjectRankingResponse> rankings = new ArrayList<>();
    int rank = 1;
    for (var entry :
        projectScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .toList()) {
      rankings.add(
          new ProjectRankingResponse(
              entry.getKey(),
              "Project " + entry.getKey(),
              rank++,
              entry.getValue()));
    }

    return rankings;
  }

  @Transactional
  public void deleteScoringModel(UUID modelId) {
    ScoringModel model =
        scoringModelRepository
            .findById(modelId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ScoringModel", modelId));

    // Delete all criteria
    List<ScoringCriterion> criteria =
        scoringCriterionRepository.findByScoringModelIdOrderBySortOrder(modelId);
    scoringCriterionRepository.deleteAll(criteria);

    scoringModelRepository.delete(model);
    auditService.logDelete("ScoringModel", modelId);
  }
}
