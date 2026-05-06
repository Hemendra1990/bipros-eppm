package com.bipros.udf.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "formula_version", schema = "udf",
       indexes = {
           @Index(name = "idx_fv_formula_project", columnList = "formula_code, project_id"),
           @Index(name = "idx_fv_created", columnList = "created_at")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormulaVersion extends BaseEntity {

    @Column(name = "formula_code", nullable = false, length = 100)
    private String formulaCode;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "expression", nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "reverted_by")
    private String revertedBy;

    @Column(name = "reverted_at")
    private java.time.Instant revertedAt;
}
