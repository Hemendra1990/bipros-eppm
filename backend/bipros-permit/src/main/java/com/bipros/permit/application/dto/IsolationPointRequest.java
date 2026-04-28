package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.IsolationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IsolationPointRequest(
        @NotNull IsolationType isolationType,
        @NotBlank String pointLabel,
        String lockNumber
) {}
