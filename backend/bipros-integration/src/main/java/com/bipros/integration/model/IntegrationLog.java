package com.bipros.integration.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "integration_logs", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationLog extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "integration_config_id", nullable = false, foreignKey = @ForeignKey(name = "fk_integration_log_config"))
    private IntegrationConfig integrationConfig;

    @Column(name = "direction", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private LogStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public enum LogStatus {
        SUCCESS,
        FAILED,
        TIMEOUT,
        PENDING
    }
}
