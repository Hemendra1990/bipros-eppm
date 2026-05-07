package com.bipros.project.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Photo / image attached to a {@link DailyProgressReport}. Lifecycle is independent of the
 * resource child rows: photos are uploaded after the DPR is saved (the API client posts the
 * DPR row first, then sends the multipart photo request against the returned id), and they
 * are deleted explicitly. {@code dprId} is a soft FK; the storage service deletes the binary
 * when the row is removed.
 */
@Entity
@Table(
    name = "dpr_attachments",
    schema = "project",
    indexes = {
        @Index(name = "idx_dpr_attachments_dpr", columnList = "dpr_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DprAttachment extends BaseEntity {

    @Column(name = "dpr_id", nullable = false)
    private UUID dprId;

    /** Denormalised so we can query attachments without joining DPR. */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** Path relative to the DPR storage root, of the form {dprId}/{uuid}.{ext}. */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "caption", length = 500)
    private String caption;

    /** EXIF capture timestamp when present; null otherwise. */
    @Column(name = "captured_at")
    private Instant capturedAt;
}
