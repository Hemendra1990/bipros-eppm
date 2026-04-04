package com.bipros.evm.domain.repository;

import com.bipros.evm.domain.entity.EvmCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvmCalculationRepository extends JpaRepository<EvmCalculation, UUID> {
    List<EvmCalculation> findByProjectIdOrderByDataDateDesc(UUID projectId);
    Optional<EvmCalculation> findTopByProjectIdOrderByDataDateDesc(UUID projectId);
    List<EvmCalculation> findByProjectIdAndDataDate(UUID projectId, LocalDate dataDate);
    List<EvmCalculation> findByProjectIdAndWbsNodeId(UUID projectId, UUID wbsNodeId);
    List<EvmCalculation> findByProjectIdAndWbsNodeIdOrderByDataDateDesc(UUID projectId, UUID wbsNodeId);
    Optional<EvmCalculation> findTopByProjectIdAndWbsNodeIdOrderByDataDateDesc(UUID projectId, UUID wbsNodeId);
}
