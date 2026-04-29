package com.bipros.project.application.dto;

import com.bipros.common.web.json.Views;
import com.bipros.contract.domain.model.ContractType;
import com.bipros.project.domain.model.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonView;

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
    String category,
    String morthCode,
    Long fromChainageM,
    Long toChainageM,
    String fromLocation,
    String toLocation,
    BigDecimal totalLengthKm,
    UUID calendarId,
    UUID activeBaselineId,
    ContractSummary contract,
    BigDecimal originalBudget,
    BigDecimal currentBudget,
    String budgetCurrency,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime archivedAt
) {

    /** Flat view of the primary {@code Contract} for this project. {@code null} when no contract
     * has been registered yet. Money fields are FinanceConfidential — non-finance/non-admin
     * callers see {@code null} for them via the role-aware {@code @JsonView} pipeline. */
    public record ContractSummary(
        UUID contractId,
        String contractNumber,
        ContractType contractType,
        @JsonView(Views.FinanceConfidential.class) BigDecimal contractValue,
        @JsonView(Views.FinanceConfidential.class) BigDecimal revisedValue,
        LocalDate startDate,
        LocalDate completionDate,
        Integer dlpMonths
    ) {}
}
