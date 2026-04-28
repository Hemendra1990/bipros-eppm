package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit_isolation_point", schema = "permit", indexes = {
        @Index(name = "ix_permit_isolation_permit", columnList = "permit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitIsolationPoint extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "isolation_type", nullable = false, length = 20)
    private IsolationType isolationType;

    @Column(name = "point_label", nullable = false, length = 200)
    private String pointLabel;

    @Column(name = "lock_number", length = 60)
    private String lockNumber;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;
}
