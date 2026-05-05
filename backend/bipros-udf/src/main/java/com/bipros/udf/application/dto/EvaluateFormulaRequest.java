package com.bipros.udf.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluateFormulaRequest {
    private String formulaCode;
    private UUID projectId;
    private Map<String, String> variables;
}
