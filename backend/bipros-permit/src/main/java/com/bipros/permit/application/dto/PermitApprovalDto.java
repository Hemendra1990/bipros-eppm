package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.ApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record PermitApprovalDto(
        UUID id,
        int stepNo,
        String label,
        String role,
        ApprovalStatus status,
        UUID reviewerId,
        Instant reviewedAt,
        String remarks
) {}
