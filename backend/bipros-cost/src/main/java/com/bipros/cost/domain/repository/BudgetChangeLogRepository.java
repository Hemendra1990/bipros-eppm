package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.BudgetChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetChangeLogRepository extends JpaRepository<BudgetChangeLog, UUID> {

    List<BudgetChangeLog> findByProjectIdOrderByRequestedAtDesc(UUID projectId);

    List<BudgetChangeLog> findByProjectIdAndStatusOrderByRequestedAtDesc(UUID projectId, BudgetChangeLog.ChangeStatus status);
}
