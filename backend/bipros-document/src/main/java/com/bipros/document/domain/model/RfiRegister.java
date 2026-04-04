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
@Table(name = "rfi_registers", schema = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RfiRegister extends BaseEntity {

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100, unique = true)
    private String rfiNumber;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String raisedBy;

    @Column(nullable = false, length = 100)
    private String assignedTo;

    @Column(nullable = false)
    private LocalDate raisedDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = true)
    private LocalDate closedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RfiStatus status = RfiStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RfiPriority priority = RfiPriority.MEDIUM;

    @Column(columnDefinition = "TEXT")
    private String response;
}
