package com.bipros.project.application.dto;

import com.bipros.contract.domain.model.ContractType;
import com.bipros.project.domain.model.ProjectCategory;
import com.bipros.project.domain.model.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Project Master response. Per "PMS MasterData UI Screens Final" Screen 01, this merges the
 * Project entity with a read-only summary of the project's primary Contract so the UI can
 * render contract number / type / value / revised value / dates / DLP months on a single
 * form without a second round trip.
 */
public record ProjectResponse(
    UUID id,
    String code,
    String name,
    String description,
    UUID epsNodeId,
    UUID obsNodeId,
    LocalDate plannedStartDate,
    LocalDate plannedFinishDate,
    LocalDate dataDate,
    ProjectStatus status,
    LocalDate mustFinishByDate,
    Integer priority,
    ProjectCategory category,
    String morthCode,
    Long fromChainageM,
    Long toChainageM,
    String fromLocation,
    String toLocation,
    BigDecimal totalLengthKm,
    ContractSummary contract,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /** Flat view of the primary {@code Contract} for this project. {@code null} when no contract
     * has been registered yet. */
    public record ContractSummary(
        UUID contractId,
        String contractNumber,
        ContractType contractType,
        BigDecimal contractValue,
        BigDecimal revisedValue,
        LocalDate startDate,
        LocalDate completionDate,
        Integer dlpMonths
    ) {}
}
