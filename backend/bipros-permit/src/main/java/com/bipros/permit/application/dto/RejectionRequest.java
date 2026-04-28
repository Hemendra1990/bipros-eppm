package com.bipros.permit.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectionRequest(
        @NotBlank @Size(max = 4000) String reason
) {}
