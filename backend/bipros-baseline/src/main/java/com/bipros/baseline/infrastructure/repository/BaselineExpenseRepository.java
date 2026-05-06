package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaselineExpenseRepository extends JpaRepository<BaselineExpense, UUID> {

  List<BaselineExpense> findByBaselineId(UUID baselineId);

  List<BaselineExpense> findByBaselineIdAndActivityId(UUID baselineId, UUID activityId);

  void deleteByBaselineId(UUID baselineId);
}
