package com.bipros.portfolio.application.dto;

import com.bipros.portfolio.domain.Portfolio;

import java.time.Instant;
import java.util.UUID;

public record PortfolioResponse(
    UUID id,
    String name,
    String description,
    UUID ownerId,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {

  public static PortfolioResponse from(Portfolio portfolio) {
    return new PortfolioResponse(
        portfolio.getId(),
        portfolio.getName(),
        portfolio.getDescription(),
        portfolio.getOwnerId(),
        portfolio.getIsActive(),
        portfolio.getCreatedAt(),
        portfolio.getUpdatedAt());
  }
}
