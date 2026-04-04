package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.PortfolioProject;

import java.time.Instant;
import java.util.UUID;

public record PortfolioProjectResponse(
    UUID id,
    UUID portfolioId,
    UUID projectId,
    Double priorityScore,
    Instant createdAt,
    Instant updatedAt) {

  public static PortfolioProjectResponse from(PortfolioProject portfolioProject) {
    return new PortfolioProjectResponse(
        portfolioProject.getId(),
        portfolioProject.getPortfolioId(),
        portfolioProject.getProjectId(),
        portfolioProject.getPriorityScore(),
        portfolioProject.getCreatedAt(),
        portfolioProject.getUpdatedAt());
  }
}
