package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.PortfolioScenarioProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioScenarioProjectRepository
    extends JpaRepository<PortfolioScenarioProject, UUID> {

  List<PortfolioScenarioProject> findByScenarioId(UUID scenarioId);

  void deleteByScenarioId(UUID scenarioId);
}
