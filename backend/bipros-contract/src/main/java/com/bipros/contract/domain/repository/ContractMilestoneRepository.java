package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.ContractMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractMilestoneRepository extends JpaRepository<ContractMilestone, UUID> {
    List<ContractMilestone> findByContractId(UUID contractId);
}
