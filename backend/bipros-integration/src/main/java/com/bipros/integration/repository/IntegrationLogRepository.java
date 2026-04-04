package com.bipros.integration.repository;

import com.bipros.integration.model.IntegrationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {
    Page<IntegrationLog> findByIntegrationConfigIdOrderByCreatedAtDesc(UUID integrationConfigId, Pageable pageable);
}
