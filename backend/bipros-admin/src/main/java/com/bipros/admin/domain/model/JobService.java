package com.bipros.admin.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_services", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobService extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private UUID projectId;

    @Column(length = 50)
    private String status = "IDLE";

    @Column
    private Instant lastRunAt;

    @Column
    private Instant nextRunAt;

    @Column
    private String cronExpression;

    @Column
    private Long lastDurationMs;

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
