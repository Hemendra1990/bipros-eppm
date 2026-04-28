package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.IsolationType;

import java.time.Instant;
import java.util.UUID;

public record IsolationPointDto(
        UUID id,
        IsolationType isolationType,
        String pointLabel,
        String lockNumber,
        Instant appliedAt,
        UUID appliedBy,
        Instant removedAt,
        UUID removedBy
) {}
