package com.bipros.gis.domain.model;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Audit row for one SatelliteIngestionService run (manual or scheduled). */
@Entity
@Table(name = "satellite_scene_ingestion_log", schema = "gis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SatelliteSceneIngestionLog extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String vendorId;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false)
    private Instant runStartedAt;

    @Column
    private Instant runFinishedAt;

    @Column(nullable = false)
    private Integer scenesFetched = 0;

    @Column(nullable = false)
    private Integer snapshotsCreated = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(columnDefinition = "TEXT")
    private String errorsJson;

    public enum Status { RUNNING, COMPLETED, FAILED, PARTIAL }
}
