package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.ActivityExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityExpenseRepository extends JpaRepository<ActivityExpense, UUID> {
    List<ActivityExpense> findByProjectId(UUID projectId);
    List<ActivityExpense> findByActivityId(UUID activityId);
    List<ActivityExpense> findByProjectIdAndActivityId(UUID projectId, UUID activityId);
    List<ActivityExpense> findByCostAccountId(UUID costAccountId);
}
