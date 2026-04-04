package com.bipros.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        List<FieldError> details
) {
    public record FieldError(String field, String reason) {}
}
