package com.bipros.udf.application.dto;

import com.bipros.udf.domain.model.UdfDataType;
import com.bipros.udf.domain.model.UdfScope;
import com.bipros.udf.domain.model.UdfSubject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserDefinedFieldRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Data type is required")
    private UdfDataType dataType;

    @NotNull(message = "Subject is required")
    private UdfSubject subject;

    private UdfScope scope = UdfScope.GLOBAL;
    private UUID projectId;
    private Boolean isFormula = false;
    private String formulaExpression;
    private String defaultValue;
    private int sortOrder;
}
