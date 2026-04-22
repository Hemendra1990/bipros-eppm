package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloActivityStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonteCarloActivityStatRepository extends JpaRepository<MonteCarloActivityStat, UUID> {

    List<MonteCarloActivityStat> findBySimulationIdOrderByCriticalityIndexDesc(UUID simulationId);

    List<MonteCarloActivityStat> findBySimulationId(UUID simulationId);

    void deleteBySimulationId(UUID simulationId);
}
