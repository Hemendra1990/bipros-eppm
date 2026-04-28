package com.bipros.permit.application.dto;

import jakarta.validation.constraints.Size;

public record ClosePermitRequest(
        @Size(max = 4000) String remarks
) {}
