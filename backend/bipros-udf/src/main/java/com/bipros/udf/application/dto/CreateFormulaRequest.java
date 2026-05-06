package com.bipros.udf.application.dto;

import com.bipros.udf.domain.model.FormulaCategory;
import com.bipros.udf.domain.model.FormulaOutputType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.RoundingMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFormulaRequest {
    private String code;
    private String name;
    private FormulaCategory category;
    private String description;
    private String defaultExpression;
    private String inputVariablesJson;
    private FormulaOutputType outputType;
    private Integer scale;
    private RoundingMode roundingMode;
    private String zeroDefault;
    private Boolean isActive;
    private Boolean isEditable;
    private Integer sortOrder;
    private String moduleSource;
}
