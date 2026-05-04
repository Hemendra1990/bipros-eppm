package com.bipros.ai.job;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent state of one async WBS-from-document generation job.
 * The frontend polls a job by id; the worker writes progress here in
 * separate transactions so polls always see fresh state.
 */
@Entity
@Table(name = "wbs_ai_jobs", schema = "ai")
@Getter
@Setter
public class WbsAiJob extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WbsAiJobStatus status = WbsAiJobStatus.PENDING;

    /** UUID of the user who created the job (separate from the audit createdBy String on BaseEntity). */
    @Column(name = "creator_user_id")
    private UUID creatorUserId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** Snapshot of WbsAiGenerateFromDocumentRequest as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "progress_stage", length = 50)
    private String progressStage;

    @Column(name = "progress_pct")
    private Integer progressPct;

    /** Serialized WbsAiGenerationResponse on success. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "model", length = 100)
    private String model;

    /** Set by DELETE /jobs/{id}; checked by the worker at progress checkpoints. */
    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested = false;
}
