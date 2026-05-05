package com.bipros.udf.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.RoundingMode;

@Entity
@Table(name = "formula_master", schema = "udf",
       indexes = {
           @Index(name = "idx_formula_master_code", columnList = "code", unique = true),
           @Index(name = "idx_formula_master_category", columnList = "category")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormulaMaster extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormulaCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_expression", nullable = false, columnDefinition = "TEXT")
    private String defaultExpression;

    @Column(name = "input_variables", columnDefinition = "TEXT")
    private String inputVariablesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_type", nullable = false, length = 20)
    private FormulaOutputType outputType;

    @Column(nullable = false)
    private Integer scale = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false, length = 20)
    private RoundingMode roundingMode = RoundingMode.HALF_UP;

    @Column(name = "zero_default")
    private String zeroDefault = "0";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_editable", nullable = false)
    private Boolean isEditable = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "module_source", length = 100)
    private String moduleSource;

    @Column(name = "formula_version")
    private Long formulaVersion;
}
