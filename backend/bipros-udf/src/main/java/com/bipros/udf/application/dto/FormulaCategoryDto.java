package com.bipros.udf.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaCategoryDto {
    private String code;
    private String name;
    private String description;
    private List<FormulaDto> formulas;
}
