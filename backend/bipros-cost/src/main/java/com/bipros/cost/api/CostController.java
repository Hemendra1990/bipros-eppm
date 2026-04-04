package com.bipros.cost.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.cost.application.dto.*;
import com.bipros.cost.application.service.CostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    // Cost Account Endpoints
    @PostMapping("/cost-accounts")
    public ResponseEntity<ApiResponse<CostAccountDto>> createCostAccount(
            @Valid @RequestBody CreateCostAccountRequest request) {
        CostAccountDto response = costService.createCostAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/cost-accounts")
    public ResponseEntity<ApiResponse<List<CostAccountDto>>> getCostAccountTree() {
        List<CostAccountDto> response = costService.getCostAccountTree();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/cost-accounts/{id}")
    public ResponseEntity<ApiResponse<CostAccountDto>> getCostAccount(@PathVariable UUID id) {
        CostAccountDto response = costService.getCostAccount(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/cost-accounts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCostAccount(@PathVariable UUID id) {
        costService.deleteCostAccount(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Activity Expense Endpoints
    @PostMapping("/projects/{projectId}/expenses")
    public ResponseEntity<ApiResponse<ActivityExpenseDto>> createActivityExpense(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateActivityExpenseRequest request) {
        ActivityExpenseDto response = costService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/projects/{projectId}/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<ActivityExpenseDto>> updateActivityExpense(
            @PathVariable UUID projectId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody UpdateActivityExpenseRequest request) {
        ActivityExpenseDto response = costService.updateExpense(expenseId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/expenses")
    public ResponseEntity<ApiResponse<List<ActivityExpenseDto>>> getProjectExpenses(
            @PathVariable UUID projectId) {
        List<ActivityExpenseDto> response = costService.getExpensesByProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/activities/{activityId}/expenses")
    public ResponseEntity<ApiResponse<List<ActivityExpenseDto>>> getActivityExpenses(
            @PathVariable UUID projectId,
            @PathVariable UUID activityId) {
        List<ActivityExpenseDto> response = costService.getExpensesByActivity(activityId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/projects/{projectId}/expenses/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> deleteActivityExpense(
            @PathVariable UUID projectId,
            @PathVariable UUID expenseId) {
        costService.deleteExpense(expenseId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Funding Source Endpoints
    @PostMapping("/funding-sources")
    public ResponseEntity<ApiResponse<FundingSourceDto>> createFundingSource(
            @Valid @RequestBody CreateFundingSourceRequest request) {
        FundingSourceDto response = costService.createFundingSource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/funding-sources")
    public ResponseEntity<ApiResponse<List<FundingSourceDto>>> getAllFundingSources() {
        List<FundingSourceDto> response = costService.getAllFundingSources();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/funding-sources/{id}")
    public ResponseEntity<ApiResponse<FundingSourceDto>> getFundingSource(@PathVariable UUID id) {
        FundingSourceDto response = costService.getFundingSource(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/funding-sources/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFundingSource(@PathVariable UUID id) {
        costService.deleteFundingSource(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Project Funding Endpoints
    @PostMapping("/projects/{projectId}/funding")
    public ResponseEntity<ApiResponse<ProjectFundingDto>> assignFundingToProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectFundingRequest request) {
        ProjectFundingDto response = costService.assignFundingToProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/funding")
    public ResponseEntity<ApiResponse<List<ProjectFundingDto>>> getProjectFunding(
            @PathVariable UUID projectId) {
        List<ProjectFundingDto> response = costService.getProjectFunding(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/projects/{projectId}/funding/{fundingId}")
    public ResponseEntity<ApiResponse<Void>> deleteProjectFunding(
            @PathVariable UUID projectId,
            @PathVariable UUID fundingId) {
        costService.deleteProjectFunding(fundingId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Financial Period Endpoints
    @PostMapping("/financial-periods")
    public ResponseEntity<ApiResponse<FinancialPeriodDto>> createFinancialPeriod(
            @Valid @RequestBody CreateFinancialPeriodRequest request) {
        FinancialPeriodDto response = costService.createFinancialPeriod(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/financial-periods")
    public ResponseEntity<ApiResponse<List<FinancialPeriodDto>>> getAllFinancialPeriods() {
        List<FinancialPeriodDto> response = costService.getAllFinancialPeriods();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/financial-periods/open")
    public ResponseEntity<ApiResponse<List<FinancialPeriodDto>>> getOpenFinancialPeriods() {
        List<FinancialPeriodDto> response = costService.getOpenFinancialPeriods();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/financial-periods/{id}")
    public ResponseEntity<ApiResponse<FinancialPeriodDto>> getFinancialPeriod(@PathVariable UUID id) {
        FinancialPeriodDto response = costService.getFinancialPeriod(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/financial-periods/{id}/close")
    public ResponseEntity<ApiResponse<FinancialPeriodDto>> closePeriod(@PathVariable UUID id) {
        FinancialPeriodDto response = costService.closePeriod(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/financial-periods/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFinancialPeriod(@PathVariable UUID id) {
        costService.deleteFinancialPeriod(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Store Period Performance Endpoints
    @PostMapping("/projects/{projectId}/spp")
    public ResponseEntity<ApiResponse<StorePeriodPerformanceDto>> createStorePeriodPerformance(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateStorePeriodPerformanceRequest request) {
        StorePeriodPerformanceDto response = costService.storePeriodPerformance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/spp")
    public ResponseEntity<ApiResponse<List<StorePeriodPerformanceDto>>> getProjectPeriodPerformance(
            @PathVariable UUID projectId) {
        List<StorePeriodPerformanceDto> response = costService.getProjectPeriodPerformance(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/spp/{periodId}")
    public ResponseEntity<ApiResponse<StorePeriodPerformanceDto>> getProjectLevelPerformance(
            @PathVariable UUID projectId,
            @PathVariable UUID periodId) {
        StorePeriodPerformanceDto response = costService.getProjectLevelPerformance(projectId, periodId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/projects/{projectId}/spp/{sppId}")
    public ResponseEntity<ApiResponse<Void>> deleteStorePeriodPerformance(
            @PathVariable UUID projectId,
            @PathVariable UUID sppId) {
        costService.deleteStorePeriodPerformance(sppId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
