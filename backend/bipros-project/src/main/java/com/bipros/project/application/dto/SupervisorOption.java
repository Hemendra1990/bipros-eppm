package com.bipros.project.application.dto;

import java.util.UUID;

/**
 * Lightweight option used by the Capacity-Utilization page's supervisor filter dropdown — only
 * supervisors who actually filed at least one DPR in the requested window are returned, so
 * users never see empty rows.
 */
public record SupervisorOption(
    UUID supervisorResourceId,
    String supervisorCode,
    String supervisorName,
    long dprCount) {}
