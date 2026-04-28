package com.bipros.analytics.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One row per analytics-assistant interaction. Captures the user, the LLM provider/model,
 * which tool was dispatched, the SQL executed (if any), token + cost telemetry, and the
 * final outcome status. Persisted via {@code AnalyticsAuditService} with a REQUIRES_NEW
 * transaction so the audit row survives outer rollbacks (e.g. tool errors).
 */
@Entity
@Table(name = "analytics_audit_log", schema = "analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsAuditLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "project_context_id")
    private UUID projectContextId;

    @Column(name = "llm_provider", length = 32)
    private String llmProvider;

    @Column(name = "llm_model", length = 128)
    private String llmModel;

    @Column(name = "tool_called", length = 64)
    private String toolCalled;

    @Column(name = "tool_args_json", columnDefinition = "TEXT")
    private String toolArgsJson;

    @Column(name = "sql_executed", columnDefinition = "TEXT")
    private String sqlExecuted;

    @Column(name = "sql_hash", length = 64)
    private String sqlHash;

    @Column(name = "result_row_count")
    private Integer resultRowCount;

    @Column(name = "result_hash", length = 64)
    private String resultHash;

    @Column(name = "narrative_returned", columnDefinition = "TEXT")
    private String narrativeReturned;

    @Column(name = "tokens_input")
    private Integer tokensInput;

    @Column(name = "tokens_output")
    private Integer tokensOutput;

    @Column(name = "cost_micros")
    private Long costMicros;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status;

    @Column(name = "error_kind", length = 64)
    private String errorKind;

    @Column(name = "request_id", length = 64)
    private String requestId;

    public enum Status {
        SUCCESS, REFUSED, LLM_ERROR, TOOL_ERROR, SQL_REJECTED, RATE_LIMITED, NOT_CONFIGURED
    }
}
