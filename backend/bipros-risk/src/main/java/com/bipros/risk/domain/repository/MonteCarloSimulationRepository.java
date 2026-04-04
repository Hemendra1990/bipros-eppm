package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonteCarloSimulationRepository extends JpaRepository<MonteCarloSimulation, UUID> {
    List<MonteCarloSimulation> findByProjectId(UUID projectId);

    @Query(value = "SELECT * FROM risk.monte_carlo_simulations WHERE project_id = ?1 ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<MonteCarloSimulation> findLatestByProjectId(UUID projectId);

    List<MonteCarloSimulation> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
