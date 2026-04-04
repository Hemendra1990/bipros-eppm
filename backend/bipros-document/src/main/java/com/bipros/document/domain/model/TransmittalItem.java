package com.bipros.document.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "transmittal_items", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransmittalItem extends BaseEntity {

    @Column(nullable = false)
    private UUID transmittalId;

    @Column(nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmittalPurpose purpose;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
