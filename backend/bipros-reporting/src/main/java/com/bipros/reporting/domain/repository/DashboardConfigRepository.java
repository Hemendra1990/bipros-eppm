package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, UUID> {
    Optional<DashboardConfig> findByTier(DashboardConfig.DashboardTier tier);
    List<DashboardConfig> findByIsDefaultTrue();
}
