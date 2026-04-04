package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonteCarloResultRepository extends JpaRepository<MonteCarloResult, UUID> {
    List<MonteCarloResult> findBySimulationIdOrderByIterationNumber(UUID simulationId);

    void deleteBySimulationId(UUID simulationId);
}
