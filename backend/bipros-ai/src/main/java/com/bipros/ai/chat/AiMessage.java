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
@Table(schema = "ai", name = "ai_messages")
@Getter
@Setter
public class AiMessage {

    @Id
    private UUID id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "seq")
    private Integer seq;

    @Column(name = "role")
    private String role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private String toolCalls;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "model")
    private String model;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "created_at")
    private Instant createdAt;
}
