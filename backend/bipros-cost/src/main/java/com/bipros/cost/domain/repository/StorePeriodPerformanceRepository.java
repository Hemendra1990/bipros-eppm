package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.StorePeriodPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorePeriodPerformanceRepository extends JpaRepository<StorePeriodPerformance, UUID> {
    List<StorePeriodPerformance> findByProjectId(UUID projectId);
    List<StorePeriodPerformance> findByProjectIdAndFinancialPeriodId(UUID projectId, UUID financialPeriodId);
    Optional<StorePeriodPerformance> findByProjectIdAndFinancialPeriodIdAndActivityIdIsNull(UUID projectId, UUID financialPeriodId);
    List<StorePeriodPerformance> findByActivityId(UUID activityId);
}
