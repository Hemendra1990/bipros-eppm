package com.bipros.analytics.etl.watermark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "analytics", name = "etl_watermark")
@IdClass(EtlWatermark.WatermarkId.class)
@Getter
@Setter
public class EtlWatermark {

    @Id
    @Column(name = "source_table")
    private String sourceTable;

    @Id
    @Column(name = "target_table")
    private String targetTable;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_synced_id")
    private UUID lastSyncedId;

    @Column(name = "rows_processed")
    private Long rowsProcessed;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public record WatermarkId(String sourceTable, String targetTable) implements Serializable {
    }
}
