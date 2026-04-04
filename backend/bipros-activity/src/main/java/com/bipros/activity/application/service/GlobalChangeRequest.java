package com.bipros.activity.application.service;

import jakarta.validation.constraints.NotBlank;

public record GlobalChangeRequest(
    @NotBlank(message = "Filter field is required")
    String filterField,

    @NotBlank(message = "Filter value is required")
    String filterValue,

    @NotBlank(message = "Update field is required")
    String updateField,

    @NotBlank(message = "Update value is required")
    String updateValue,

    GlobalChangeOperation operation
) {}
