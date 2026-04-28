package com.bipros.permit.application.dto;

import com.bipros.permit.domain.model.PermitStatus;

import java.time.Instant;

/** Public-safe verification payload (no PII). */
public record QrVerifyResponse(
        String permitCode,
        String permitTypeName,
        PermitStatus status,
        String locationZone,
        Instant validFrom,
        Instant validTo,
        boolean valid
) {}
