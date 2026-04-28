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

import java.util.UUID;

@Entity
@Table(name = "permit_worker", schema = "permit", indexes = {
        @Index(name = "ix_permit_worker_permit", columnList = "permit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermitWorker extends BaseEntity {

    @Column(name = "permit_id", nullable = false)
    private UUID permitId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "civil_id", length = 60)
    private String civilId;

    @Column(length = 60)
    private String nationality;

    @Column(length = 100)
    private String trade;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_on_permit", nullable = false, length = 20)
    private WorkerRole roleOnPermit = WorkerRole.PRINCIPAL;

    @Column(name = "training_certs_json", columnDefinition = "TEXT")
    private String trainingCertsJson;
}
