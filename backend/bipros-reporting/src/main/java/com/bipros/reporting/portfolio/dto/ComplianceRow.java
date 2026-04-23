package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplianceRow(
    UUID projectId,
    String projectCode,
    String projectName,
    Boolean pfmsSanctionOk,
    Boolean gstnCheckOk,
    Boolean gemLinkedOk,
    Boolean cpppPublishedOk,
    Boolean pariveshClearanceOk,
    double overallScore) {}
