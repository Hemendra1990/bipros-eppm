package com.bipros.portfolio.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.portfolio.application.dto.CreatePortfolioRequest;
import com.bipros.portfolio.application.dto.OptimizationResultResponse;
import com.bipros.portfolio.application.dto.OptimizePortfolioRequest;
import com.bipros.portfolio.application.dto.PortfolioProjectResponse;
import com.bipros.portfolio.application.dto.PortfolioResponse;
import com.bipros.portfolio.application.dto.ScenarioComparisonResponse;
import com.bipros.portfolio.application.dto.UpdatePortfolioRequest;
import com.bipros.portfolio.application.dto.WhatIfRequest;
import com.bipros.portfolio.application.dto.WhatIfResponse;
import com.bipros.portfolio.domain.Portfolio;
import com.bipros.portfolio.domain.PortfolioProject;
import com.bipros.portfolio.domain.PortfolioScenario;
import com.bipros.portfolio.domain.PortfolioScenarioProject;
import com.bipros.portfolio.domain.algorithm.PortfolioOptimizer;
import com.bipros.portfolio.domain.algorithm.PortfolioOptimizer.ProjectCandidate;
import com.bipros.portfolio.infrastructure.repository.PortfolioProjectRepository;
import com.bipros.portfolio.infrastructure.repository.PortfolioRepository;
import com.bipros.portfolio.infrastructure.repository.PortfolioScenarioProjectRepository;
import com.bipros.portfolio.infrastructure.repository.PortfolioScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioService {

  private final PortfolioRepository portfolioRepository;
  private final PortfolioProjectRepository portfolioProjectRepository;
  private final PortfolioScenarioRepository portfolioScenarioRepository;
  private final PortfolioScenarioProjectRepository portfolioScenarioProjectRepository;
  private final ScoringService scoringService;
  private final AuditService auditService;
  private final PortfolioOptimizer portfolioOptimizer = new PortfolioOptimizer();

  public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
    Portfolio portfolio = new Portfolio();
    portfolio.setName(request.name());
    portfolio.setDescription(request.description());
    portfolio.setIsActive(true);

    Portfolio saved = portfolioRepository.save(portfolio);
    auditService.logCreate("Portfolio", saved.getId(), saved);
    return PortfolioResponse.from(saved);
  }

  public PortfolioResponse getPortfolio(UUID portfolioId) {
    Portfolio portfolio =
        portfolioRepository
            .findById(portfolioId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Portfolio", portfolioId));
    return PortfolioResponse.from(portfolio);
  }

  public List<PortfolioResponse> listPortfolios() {
    return portfolioRepository.findByIsActiveTrue().stream()
        .map(PortfolioResponse::from)
        .toList();
  }

  @Transactional
  public PortfolioProjectResponse addProject(UUID portfolioId, UUID projectId) {
    Portfolio portfolio =
        portfolioRepository
            .findById(portfolioId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Portfolio", portfolioId));

    // Check if project already exists in portfolio
    if (portfolioProjectRepository.findByPortfolioIdAndProjectId(portfolioId, projectId)
        .isPresent()) {
      throw new IllegalArgumentException("Project already in portfolio");
    }

    PortfolioProject portfolioProject = new PortfolioProject();
    portfolioProject.setPortfolioId(portfolioId);
    portfolioProject.setProjectId(projectId);
    portfolioProject.setPriorityScore(0.0);

    PortfolioProject saved = portfolioProjectRepository.save(portfolioProject);
    auditService.logCreate("PortfolioProject", saved.getId(), saved);
    return PortfolioProjectResponse.from(saved);
  }

  @Transactional
  public void removeProject(UUID portfolioId, UUID projectId) {
    Portfolio portfolio =
        portfolioRepository
            .findById(portfolioId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Portfolio", portfolioId));

    portfolioProjectRepository.deleteByPortfolioIdAndProjectId(portfolioId, projectId);
    auditService.logDelete("PortfolioProject", projectId);
  }

  public List<PortfolioProjectResponse> getPortfolioProjects(UUID portfolioId) {
    return portfolioProjectRepository.findByPortfolioId(portfolioId).stream()
        .map(PortfolioProjectResponse::from)
        .toList();
  }

  @Transactional
  public void deletePortfolio(UUID portfolioId) {
    Portfolio portfolio =
        portfolioRepository
            .findById(portfolioId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Portfolio", portfolioId));

    // Delete all projects from portfolio
    List<PortfolioProject> projects = portfolioProjectRepository.findByPortfolioId(portfolioId);
    portfolioProjectRepository.deleteAll(projects);

    portfolioRepository.delete(portfolio);
    auditService.logDelete("Portfolio", portfolioId);
  }

  @Transactional
  public PortfolioResponse update(UUID portfolioId, UpdatePortfolioRequest request) {
    Portfolio portfolio =
        portfolioRepository
            .findById(portfolioId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Portfolio", portfolioId));

    auditService.logUpdate("Portfolio", portfolioId, "name", portfolio.getName(), request.name());
    auditService.logUpdate("Portfolio", portfolioId, "description", portfolio.getDescription(), request.description());

    portfolio.setName(request.name());
    portfolio.setDescription(request.description());

    Portfolio updated = portfolioRepository.save(portfolio);
    return PortfolioResponse.from(updated);
  }

  /**
   * Optimize portfolio: select projects maximizing score within budget constraint.
   */
  @Transactional(readOnly = true)
  public OptimizationResultResponse optimizePortfolio(UUID portfolioId, OptimizePortfolioRequest request) {
    log.info("Optimizing portfolio: id={}, budgetLimit={}", portfolioId, request.budgetLimit());

    portfolioRepository.findById(portfolioId)
        .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

    List<PortfolioProject> portfolioProjects = portfolioProjectRepository.findByPortfolioId(portfolioId);
    Set<UUID> mandatoryIds = request.mandatoryProjectIds() != null
        ? Set.copyOf(request.mandatoryProjectIds())
        : Set.of();

    List<ProjectCandidate> candidates = portfolioProjects.stream()
        .map(pp -> new ProjectCandidate(
            pp.getProjectId(),
            "Project " + pp.getProjectId().toString().substring(0, 8),
            pp.getPriorityScore() != null ? pp.getPriorityScore() : 0.0,
            BigDecimal.ZERO, // budget from scenario or external — default to zero
            mandatoryIds.contains(pp.getProjectId())
        ))
        .toList();

    PortfolioOptimizer.OptimizationResult result = portfolioOptimizer.optimize(candidates, request.budgetLimit());
    return OptimizationResultResponse.from(result);
  }

  /**
   * What-if analysis: simulate adding or removing a project.
   */
  @Transactional(readOnly = true)
  public WhatIfResponse whatIfAnalysis(UUID portfolioId, WhatIfRequest request) {
    log.info("What-if analysis: portfolio={}, project={}, action={}",
        portfolioId, request.projectId(), request.addProject() ? "ADD" : "REMOVE");

    portfolioRepository.findById(portfolioId)
        .orElseThrow(() -> new ResourceNotFoundException("Portfolio", portfolioId));

    List<PortfolioProject> portfolioProjects = portfolioProjectRepository.findByPortfolioId(portfolioId);

    List<ProjectCandidate> currentSelection = portfolioProjects.stream()
        .map(pp -> new ProjectCandidate(
            pp.getProjectId(),
            "Project " + pp.getProjectId().toString().substring(0, 8),
            pp.getPriorityScore() != null ? pp.getPriorityScore() : 0.0,
            BigDecimal.ZERO,
            false
        ))
        .toList();

    // Build candidate for target project
    ProjectCandidate target;
    if (request.addProject()) {
      // Adding a project not yet in portfolio
      target = new ProjectCandidate(
          request.projectId(),
          "Project " + request.projectId().toString().substring(0, 8),
          0.0, BigDecimal.ZERO, false
      );
    } else {
      // Removing a project from portfolio
      target = currentSelection.stream()
          .filter(c -> c.projectId().equals(request.projectId()))
          .findFirst()
          .orElseThrow(() -> new ResourceNotFoundException("PortfolioProject", request.projectId()));
    }

    PortfolioOptimizer.WhatIfResult result = portfolioOptimizer.whatIf(
        currentSelection, target, request.addProject(), request.budgetLimit()
    );
    return WhatIfResponse.from(result);
  }

  /**
   * Compare two portfolio scenarios side by side.
   */
  @Transactional(readOnly = true)
  public ScenarioComparisonResponse compareScenarios(UUID portfolioId, List<UUID> scenarioIds) {
    log.info("Comparing scenarios: portfolio={}, scenarios={}", portfolioId, scenarioIds);

    if (scenarioIds == null || scenarioIds.size() != 2) {
      throw new IllegalArgumentException("Exactly 2 scenario IDs required for comparison");
    }

    PortfolioScenario scenarioA = portfolioScenarioRepository.findById(scenarioIds.get(0))
        .orElseThrow(() -> new ResourceNotFoundException("PortfolioScenario", scenarioIds.get(0)));
    PortfolioScenario scenarioB = portfolioScenarioRepository.findById(scenarioIds.get(1))
        .orElseThrow(() -> new ResourceNotFoundException("PortfolioScenario", scenarioIds.get(1)));

    List<PortfolioScenarioProject> projectsA = portfolioScenarioProjectRepository
        .findByScenarioId(scenarioA.getId()).stream()
        .filter(PortfolioScenarioProject::getIncluded)
        .toList();
    List<PortfolioScenarioProject> projectsB = portfolioScenarioProjectRepository
        .findByScenarioId(scenarioB.getId()).stream()
        .filter(PortfolioScenarioProject::getIncluded)
        .toList();

    List<ProjectCandidate> candidatesA = projectsA.stream()
        .map(sp -> new ProjectCandidate(
            sp.getProjectId(),
            "Project " + sp.getProjectId().toString().substring(0, 8),
            sp.getAdjustedPriority() != null ? sp.getAdjustedPriority().doubleValue() : 0.0,
            sp.getAdjustedBudget() != null ? sp.getAdjustedBudget() : BigDecimal.ZERO,
            false
        ))
        .toList();

    List<ProjectCandidate> candidatesB = projectsB.stream()
        .map(sp -> new ProjectCandidate(
            sp.getProjectId(),
            "Project " + sp.getProjectId().toString().substring(0, 8),
            sp.getAdjustedPriority() != null ? sp.getAdjustedPriority().doubleValue() : 0.0,
            sp.getAdjustedBudget() != null ? sp.getAdjustedBudget() : BigDecimal.ZERO,
            false
        ))
        .toList();

    PortfolioOptimizer.ScenarioComparison comparison = portfolioOptimizer.compareScenarios(
        scenarioA.getName(), candidatesA,
        scenarioB.getName(), candidatesB
    );
    return ScenarioComparisonResponse.from(comparison);
  }
}
