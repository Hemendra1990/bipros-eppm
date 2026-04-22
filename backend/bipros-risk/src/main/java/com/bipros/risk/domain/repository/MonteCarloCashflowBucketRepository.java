package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.MonteCarloCashflowBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonteCarloCashflowBucketRepository extends JpaRepository<MonteCarloCashflowBucket, UUID> {

    List<MonteCarloCashflowBucket> findBySimulationIdOrderByPeriodEndDate(UUID simulationId);

    void deleteBySimulationId(UUID simulationId);
}
