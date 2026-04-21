package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Page<Contract> findByProjectId(UUID projectId, Pageable pageable);
    List<Contract> findByProjectId(UUID projectId);
    List<Contract> findByTenderId(UUID tenderId);
    List<Contract> findByWbsPackageCode(String wbsPackageCode);
    java.util.Optional<Contract> findByContractNumber(String contractNumber);
}
