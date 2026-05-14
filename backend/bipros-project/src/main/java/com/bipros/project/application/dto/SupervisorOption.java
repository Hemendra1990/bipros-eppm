package com.bipros.project.application.dto;

import java.util.UUID;

/**
 * Lightweight option used by the Capacity-Utilization page's supervisor filter dropdown — only
 * supervisors who actually filed at least one DPR (or are assigned to an activity that has DPRs
 * in the window) are returned, so users never see empty rows.
 *
 * <p>Source-agnostic: exactly one of {@code supervisorUserId} / {@code supervisorResourceId} is
 * set on each row. The caller stores both back-to-back and the report filter accepts either.
 */
public record SupervisorOption(
    UUID supervisorUserId,
    UUID supervisorResourceId,
    String supervisorCode,
    String supervisorName,
    long dprCount) {}
