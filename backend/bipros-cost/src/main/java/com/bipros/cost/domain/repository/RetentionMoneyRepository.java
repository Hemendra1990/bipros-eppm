package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.RetentionMoney;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionMoneyRepository extends JpaRepository<RetentionMoney, UUID> {
    List<RetentionMoney> findByProjectIdOrderByCreatedAt(UUID projectId);
    List<RetentionMoney> findByContractId(UUID contractId);
}
