package com.bipros.common.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, UUID entityId);

    List<AuditLog> findByUserId(UUID userId);

    List<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId);

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    List<AuditLog> findByEntityTypeOrderByTimestampDesc(String entityType);

    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);
}
