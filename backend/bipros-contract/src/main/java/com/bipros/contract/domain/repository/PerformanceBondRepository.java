package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.PerformanceBond;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PerformanceBondRepository extends JpaRepository<PerformanceBond, UUID> {
    List<PerformanceBond> findByContractId(UUID contractId);
}
