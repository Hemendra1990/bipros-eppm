package com.bipros.permit.application.dto;

import jakarta.validation.constraints.Size;

public record ApprovalActionRequest(
        @Size(max = 4000) String remarks
) {}
