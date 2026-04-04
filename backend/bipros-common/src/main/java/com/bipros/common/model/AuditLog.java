package com.bipros.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", schema = "public", indexes = {
    @Index(name = "idx_audit_entity_type_id", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "field_name", length = 255)
    private String fieldName;

    public AuditLog(String entityType, UUID entityId, String action, UUID userId, String username, Instant timestamp) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.userId = userId;
        this.username = username;
        this.timestamp = timestamp;
    }
}
