package com.bipros.udf.application.dto;

import com.bipros.udf.domain.model.UdfDataType;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDefinedFieldDto {
    private UUID id;
    private String name;
    private String description;
    private UdfDataType dataType;
    private UdfSubject subject;
    private UdfScope scope;
    private UUID projectId;
    private Boolean isFormula;
    private String formulaExpression;
    private String defaultValue;
    private int sortOrder;
}
