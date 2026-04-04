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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transmittals", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transmittal extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100, unique = true)
    private String transmittalNumber;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 255)
    private String fromParty;

    @Column(nullable = false, length = 255)
    private String toParty;

    @Column(nullable = false)
    private LocalDate sentDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmittalStatus status = TransmittalStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
