package com.bipros.portfolio.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.portfolio.application.dto.CreatePortfolioRequest;
import com.bipros.portfolio.application.dto.PortfolioProjectResponse;
import com.bipros.portfolio.application.dto.PortfolioResponse;
import com.bipros.portfolio.domain.Portfolio;
import com.bipros.portfolio.domain.PortfolioProject;
import com.bipros.portfolio.infrastructure.repository.PortfolioProjectRepository;
import com.bipros.portfolio.infrastructure.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioService {

  private final PortfolioRepository portfolioRepository;
  private final PortfolioProjectRepository portfolioProjectRepository;

  public PortfolioResponse createPortfolio(CreatePortfolioRequest request) {
    Portfolio portfolio = new Portfolio();
    portfolio.setName(request.name());
    portfolio.setDescription(request.description());
    portfolio.setIsActive(true);

    Portfolio saved = portfolioRepository.save(portfolio);
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
  }
}
