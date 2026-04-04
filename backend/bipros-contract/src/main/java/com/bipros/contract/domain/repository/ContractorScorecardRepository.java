package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.ContractorScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractorScorecardRepository extends JpaRepository<ContractorScorecard, UUID> {
    List<ContractorScorecard> findByContractId(UUID contractId);
    Optional<ContractorScorecard> findByContractIdAndPeriod(UUID contractId, String period);
}
