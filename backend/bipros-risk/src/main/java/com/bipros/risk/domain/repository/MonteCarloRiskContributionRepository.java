package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloRiskContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonteCarloRiskContributionRepository extends JpaRepository<MonteCarloRiskContribution, UUID> {

    List<MonteCarloRiskContribution> findBySimulationIdOrderByOccurrenceRateDesc(UUID simulationId);

    void deleteBySimulationId(UUID simulationId);
}
