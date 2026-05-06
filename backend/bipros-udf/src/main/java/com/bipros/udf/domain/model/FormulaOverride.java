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
@Table(name = "formula_override", schema = "udf",
       indexes = {
           @Index(name = "idx_fo_formula_project", columnList = "formula_code, project_id", unique = true),
           @Index(name = "idx_fo_project", columnList = "project_id")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormulaOverride extends BaseEntity {

    @Column(name = "formula_code", nullable = false, length = 100)
    private String formulaCode;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "override_expression", nullable = false, columnDefinition = "TEXT")
    private String overrideExpression;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "override_version")
    private Long overrideVersion;
}
