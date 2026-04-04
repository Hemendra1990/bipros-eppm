package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.CashFlowForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashFlowForecastRepository extends JpaRepository<CashFlowForecast, UUID> {
    List<CashFlowForecast> findByProjectIdOrderByPeriodAsc(UUID projectId);
}
