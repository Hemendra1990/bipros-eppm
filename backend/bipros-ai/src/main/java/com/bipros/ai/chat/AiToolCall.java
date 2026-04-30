package com.bipros.ai.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "ai", name = "ai_tool_calls")
@Getter
@Setter
public class AiToolCall {

    @Id
    private UUID id;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "tool_name")
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb")
    private String input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private String output;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "status")
    private String status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at")
    private Instant createdAt;
}
