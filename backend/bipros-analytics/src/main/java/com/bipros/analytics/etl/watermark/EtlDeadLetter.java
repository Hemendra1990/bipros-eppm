package com.bipros.analytics.etl.watermark;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(schema = "analytics", name = "etl_dead_letter")
@Getter
@Setter
public class EtlDeadLetter extends BaseEntity {

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "error")
    private String error;

    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
}
