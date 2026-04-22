package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloMilestoneStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonteCarloMilestoneStatRepository extends JpaRepository<MonteCarloMilestoneStat, UUID> {

    List<MonteCarloMilestoneStat> findBySimulationId(UUID simulationId);

    void deleteBySimulationId(UUID simulationId);
}
