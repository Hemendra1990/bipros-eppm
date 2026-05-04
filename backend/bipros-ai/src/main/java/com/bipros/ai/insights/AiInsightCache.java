package com.bipros.ai.insights;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_insight_cache", schema = "ai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsightCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "tab", nullable = false, length = 64)
    private String tab;

    @Column(name = "data_hash", nullable = false, length = 64)
    private String dataHash;

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "token_usage_input")
    private Integer tokenUsageInput;

    @Column(name = "token_usage_output")
    private Integer tokenUsageOutput;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
