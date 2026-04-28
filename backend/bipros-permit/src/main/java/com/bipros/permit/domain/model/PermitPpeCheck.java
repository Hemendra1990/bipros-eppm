package com.bipros.permit.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permit_ppe_check", schema = "permit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_permit_ppe", columnNames = {"permit_id", "ppe_item_template_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitPpeCheck extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Column(name = "ppe_item_template_id", nullable = false)
    private UUID ppeItemTemplateId;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
