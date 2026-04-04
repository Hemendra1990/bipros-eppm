package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.PortfolioScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioScenarioRepository extends JpaRepository<PortfolioScenario, UUID> {

  List<PortfolioScenario> findByPortfolioId(UUID portfolioId);

  List<PortfolioScenario> findByPortfolioIdAndIsBaselineTrue(UUID portfolioId);
}
