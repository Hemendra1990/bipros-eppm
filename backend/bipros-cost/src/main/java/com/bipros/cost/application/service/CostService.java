package com.bipros.cost.application.service;

import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.cost.application.dto.*;
import com.bipros.cost.domain.entity.*;
import com.bipros.cost.domain.repository.*;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CostService {

    private final CostAccountRepository costAccountRepository;
    private final ActivityExpenseRepository activityExpenseRepository;
    private final FundingSourceRepository fundingSourceRepository;
    private final ProjectFundingRepository projectFundingRepository;
    private final FinancialPeriodRepository financialPeriodRepository;
    private final StorePeriodPerformanceRepository storePeriodPerformanceRepository;
    private final RaBillRepository raBillRepository;
    private final RaBillItemRepository raBillItemRepository;
    private final DprEstimateRepository dprEstimateRepository;
    private final RetentionMoneyRepository retentionMoneyRepository;
    private final CashFlowForecastRepository cashFlowForecastRepository;
    private final CashFlowForecastEngine cashFlowForecastEngine;
    private final SatelliteGateService satelliteGateService;
    private final AuditService auditService;
    // PMS MasterData wiring — material procurement + on-hand stock enrich the cost summary.
    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final MaterialStockRepository materialStockRepository;

    // Cost Account Operations
    @Transactional
    public CostAccountDto createCostAccount(CreateCostAccountRequest request) {
        if (costAccountRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("COST_ACCOUNT_DUPLICATE_CODE",
                    "Cost account with code " + request.code() + " already exists");
        }

        var entity = new CostAccount();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sortOrder());

        var saved = costAccountRepository.save(entity);
        auditService.logCreate("CostAccount", saved.getId(), CostAccountDto.from(saved));
        return CostAccountDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CostAccountDto> getCostAccountTree() {
        return costAccountRepository.findAllByOrderBySortOrder()
                .stream()
                .map(CostAccountDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CostAccountDto getCostAccount(UUID id) {
        var entity = costAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostAccount", id));
        return CostAccountDto.from(entity);
    }

    @Transactional
    public CostAccountDto updateCostAccount(UUID id, UpdateCostAccountRequest request) {
        var entity = costAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostAccount", id));

        entity.setName(request.name());
        entity.setDescription(request.description());

        var updated = costAccountRepository.save(entity);
        auditService.logUpdate("CostAccount", id, "costAccount", null, CostAccountDto.from(updated));
        return CostAccountDto.from(updated);
    }

    @Transactional
    public void deleteCostAccount(UUID id) {
        var entity = costAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostAccount", id));

        var childrenCount = costAccountRepository.findByParentIdOrderBySortOrder(id).size();
        if (childrenCount > 0) {
            throw new BusinessRuleException("COST_ACCOUNT_HAS_CHILDREN",
                    "Cannot delete cost account with child accounts");
        }

        costAccountRepository.delete(entity);
        auditService.logDelete("CostAccount", id);
    }

    // Activity Expense Operations
    @Transactional
    public ActivityExpenseDto createExpense(CreateActivityExpenseRequest request) {
        var entity = new ActivityExpense();
        entity.setActivityId(request.activityId());
        entity.setProjectId(request.projectId());
        entity.setCostAccountId(request.costAccountId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExpenseCategory(request.expenseCategory());
        entity.setBudgetedCost(request.budgetedCost());
        entity.setActualCost(request.actualCost());
        entity.setRemainingCost(request.remainingCost());
        entity.setAtCompletionCost(request.atCompletionCost());
        entity.setPercentComplete(request.percentComplete());
        entity.setPlannedStartDate(request.plannedStartDate());
        entity.setPlannedFinishDate(request.plannedFinishDate());
        entity.setActualStartDate(request.actualStartDate());
        entity.setActualFinishDate(request.actualFinishDate());

        var saved = activityExpenseRepository.save(entity);
        auditService.logCreate("ActivityExpense", saved.getId(), ActivityExpenseDto.from(saved));
        return ActivityExpenseDto.from(saved);
    }

    @Transactional
    public ActivityExpenseDto updateExpense(UUID id, UpdateActivityExpenseRequest request) {
        var entity = activityExpenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityExpense", id));

        entity.setCostAccountId(request.costAccountId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setExpenseCategory(request.expenseCategory());
        entity.setBudgetedCost(request.budgetedCost());
        entity.setActualCost(request.actualCost());
        entity.setRemainingCost(request.remainingCost());
        entity.setAtCompletionCost(request.atCompletionCost());
        entity.setPercentComplete(request.percentComplete());
        entity.setPlannedStartDate(request.plannedStartDate());
        entity.setPlannedFinishDate(request.plannedFinishDate());
        entity.setActualStartDate(request.actualStartDate());
        entity.setActualFinishDate(request.actualFinishDate());

        var saved = activityExpenseRepository.save(entity);
        auditService.logUpdate("ActivityExpense", id, "expense", null, ActivityExpenseDto.from(saved));
        return ActivityExpenseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ActivityExpenseDto> getExpensesByProject(UUID projectId) {
        return activityExpenseRepository.findByProjectId(projectId)
                .stream()
                .map(ActivityExpenseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<ActivityExpenseDto> getExpensesByProjectPaged(UUID projectId, int page, int size) {
        var pageResult = activityExpenseRepository.findByProjectId(projectId, PageRequest.of(page, size));
        var content = pageResult.getContent().stream()
                .map(ActivityExpenseDto::from)
                .collect(Collectors.toList());
        return PagedResponse.of(content, pageResult.getTotalElements(),
                pageResult.getTotalPages(), pageResult.getNumber(), pageResult.getSize());
    }

    @Transactional(readOnly = true)
    public List<ActivityExpenseDto> getExpensesByActivity(UUID activityId) {
        return activityExpenseRepository.findByActivityId(activityId)
                .stream()
                .map(ActivityExpenseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteExpense(UUID id) {
        var entity = activityExpenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityExpense", id));
        activityExpenseRepository.delete(entity);
        auditService.logDelete("ActivityExpense", id);
    }

    // Funding Source Operations
    @Transactional
    public FundingSourceDto createFundingSource(CreateFundingSourceRequest request) {
        if (request.code() != null && fundingSourceRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("FUNDING_SOURCE_DUPLICATE_CODE",
                    "Funding source with code " + request.code() + " already exists");
        }

        var entity = new FundingSource();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCode(request.code());
        entity.setTotalAmount(request.totalAmount());
        entity.setAllocatedAmount(request.allocatedAmount());
        entity.setRemainingAmount(request.remainingAmount());

        var saved = fundingSourceRepository.save(entity);
        auditService.logCreate("FundingSource", saved.getId(), FundingSourceDto.from(saved));
        return FundingSourceDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FundingSourceDto> getAllFundingSources() {
        return fundingSourceRepository.findAll()
                .stream()
                .map(FundingSourceDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FundingSourceDto getFundingSource(UUID id) {
        var entity = fundingSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FundingSource", id));
        return FundingSourceDto.from(entity);
    }

    @Transactional
    public void deleteFundingSource(UUID id) {
        var entity = fundingSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FundingSource", id));
        fundingSourceRepository.delete(entity);
        auditService.logDelete("FundingSource", id);
    }

    // Project Funding Operations
    @Transactional
    public ProjectFundingDto assignFundingToProject(CreateProjectFundingRequest request) {
        var fundingSource = fundingSourceRepository.findById(request.fundingSourceId())
                .orElseThrow(() -> new ResourceNotFoundException("FundingSource", request.fundingSourceId()));

        var entity = new ProjectFunding();
        entity.setProjectId(request.projectId());
        entity.setFundingSourceId(request.fundingSourceId());
        entity.setWbsNodeId(request.wbsNodeId());
        entity.setAllocatedAmount(request.allocatedAmount());

        var saved = projectFundingRepository.save(entity);
        auditService.logCreate("ProjectFunding", saved.getId(), ProjectFundingDto.from(saved));
        return ProjectFundingDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectFundingDto> getProjectFunding(UUID projectId) {
        return projectFundingRepository.findByProjectId(projectId)
                .stream()
                .map(ProjectFundingDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteProjectFunding(UUID id) {
        var entity = projectFundingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectFunding", id));
        projectFundingRepository.delete(entity);
        auditService.logDelete("ProjectFunding", id);
    }

    // Financial Period Operations
    @Transactional
    public FinancialPeriodDto createFinancialPeriod(CreateFinancialPeriodRequest request) {
        var entity = new FinancialPeriod();
        entity.setName(request.name());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setPeriodType(request.periodType());
        entity.setIsClosed(false);
        entity.setSortOrder(request.sortOrder());

        var saved = financialPeriodRepository.save(entity);
        auditService.logCreate("FinancialPeriod", saved.getId(), FinancialPeriodDto.from(saved));
        return FinancialPeriodDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FinancialPeriodDto> getAllFinancialPeriods() {
        return financialPeriodRepository.findAllByOrderBySortOrder()
                .stream()
                .map(FinancialPeriodDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FinancialPeriodDto> getOpenFinancialPeriods() {
        return financialPeriodRepository.findByIsClosedFalseOrderBySortOrder()
                .stream()
                .map(FinancialPeriodDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FinancialPeriodDto getFinancialPeriod(UUID id) {
        var entity = financialPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialPeriod", id));
        return FinancialPeriodDto.from(entity);
    }

    @Transactional
    public FinancialPeriodDto closePeriod(UUID id) {
        var entity = financialPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialPeriod", id));
        entity.setIsClosed(true);
        var saved = financialPeriodRepository.save(entity);
        auditService.logUpdate("FinancialPeriod", id, "isClosed", false, true);
        return FinancialPeriodDto.from(saved);
    }

    @Transactional
    public void deleteFinancialPeriod(UUID id) {
        var entity = financialPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialPeriod", id));
        financialPeriodRepository.delete(entity);
        auditService.logDelete("FinancialPeriod", id);
    }

    // Store Period Performance Operations
    @Transactional
    public StorePeriodPerformanceDto storePeriodPerformance(CreateStorePeriodPerformanceRequest request) {
        var entity = new StorePeriodPerformance();
        entity.setProjectId(request.projectId());
        entity.setFinancialPeriodId(request.financialPeriodId());
        entity.setActivityId(request.activityId());
        entity.setActualLaborCost(request.actualLaborCost());
        entity.setActualNonlaborCost(request.actualNonlaborCost());
        entity.setActualMaterialCost(request.actualMaterialCost());
        entity.setActualExpenseCost(request.actualExpenseCost());
        entity.setActualLaborUnits(request.actualLaborUnits());
        entity.setActualNonlaborUnits(request.actualNonlaborUnits());
        entity.setActualMaterialUnits(request.actualMaterialUnits());
        entity.setEarnedValueCost(request.earnedValueCost());
        entity.setPlannedValueCost(request.plannedValueCost());

        var saved = storePeriodPerformanceRepository.save(entity);
        auditService.logCreate("StorePeriodPerformance", saved.getId(), StorePeriodPerformanceDto.from(saved));
        return StorePeriodPerformanceDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StorePeriodPerformanceDto> getProjectPeriodPerformance(UUID projectId) {
        return storePeriodPerformanceRepository.findByProjectId(projectId)
                .stream()
                .map(StorePeriodPerformanceDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StorePeriodPerformanceDto getProjectLevelPerformance(UUID projectId, UUID financialPeriodId) {
        var entity = storePeriodPerformanceRepository.findByProjectIdAndFinancialPeriodIdAndActivityIdIsNull(projectId, financialPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("StorePeriodPerformance", projectId));
        return StorePeriodPerformanceDto.from(entity);
    }

    @Transactional
    public void deleteStorePeriodPerformance(UUID id) {
        var entity = storePeriodPerformanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StorePeriodPerformance", id));
        storePeriodPerformanceRepository.delete(entity);
        auditService.logDelete("StorePeriodPerformance", id);
    }

    // RA Bill Operations
    @Transactional
    public RaBillDto createRaBill(CreateRaBillRequest request) {
        if (raBillRepository.findByBillNumber(request.billNumber()).isPresent()) {
            throw new BusinessRuleException("RABILL_DUPLICATE_NUMBER",
                    "RA Bill with number " + request.billNumber() + " already exists");
        }

        var entity = new RaBill();
        entity.setProjectId(request.projectId());
        entity.setContractId(request.contractId());
        entity.setWbsPackageCode(request.wbsPackageCode());
        entity.setBillNumber(request.billNumber());
        entity.setBillPeriodFrom(request.billPeriodFrom());
        entity.setBillPeriodTo(request.billPeriodTo());
        entity.setGrossAmount(request.grossAmount());
        applyDeductions(entity, request);
        entity.setNetAmount(request.netAmount());
        entity.setContractorClaimedPercent(request.contractorClaimedPercent());
        entity.setStatus(RaBill.RaBillStatus.DRAFT);
        entity.setRemarks(request.remarks());

        satelliteGateService.evaluate(entity);

        var saved = raBillRepository.save(entity);
        auditService.logCreate("RaBill", saved.getId(), RaBillDto.from(saved));
        return RaBillDto.from(saved);
    }

    private void applyDeductions(RaBill entity, CreateRaBillRequest request) {
        entity.setMobAdvanceRecovery(request.mobAdvanceRecovery());
        entity.setRetention5Pct(request.retention5Pct());
        entity.setTds2Pct(request.tds2Pct());
        entity.setGst18Pct(request.gst18Pct());
        BigDecimal total = BigDecimal.ZERO;
        if (request.mobAdvanceRecovery() != null) total = total.add(request.mobAdvanceRecovery());
        if (request.retention5Pct() != null) total = total.add(request.retention5Pct());
        if (request.tds2Pct() != null) total = total.add(request.tds2Pct());
        if (request.gst18Pct() != null) total = total.add(request.gst18Pct());
        if (total.signum() > 0) {
            entity.setDeductions(total);
        } else if (request.deductions() != null) {
            entity.setDeductions(request.deductions());
        } else {
            entity.setDeductions(BigDecimal.ZERO);
        }
    }

    @Transactional(readOnly = true)
    public List<RaBillDto> getRaBillsByProject(UUID projectId) {
        return raBillRepository.findByProjectIdOrderByBillNumberDesc(projectId)
                .stream()
                .map(RaBillDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RaBillDto getRaBill(UUID raBillId) {
        var entity = raBillRepository.findById(raBillId)
                .orElseThrow(() -> new ResourceNotFoundException("RaBill", raBillId));
        return RaBillDto.from(entity);
    }

    @Transactional
    public RaBillDto updateRaBill(UUID raBillId, CreateRaBillRequest request) {
        var entity = raBillRepository.findById(raBillId)
                .orElseThrow(() -> new ResourceNotFoundException("RaBill", raBillId));

        if (!entity.getStatus().equals(RaBill.RaBillStatus.DRAFT)) {
            throw new BusinessRuleException("RABILL_NOT_DRAFT",
                    "Only DRAFT RA Bills can be updated");
        }

        entity.setBillNumber(request.billNumber());
        entity.setBillPeriodFrom(request.billPeriodFrom());
        entity.setBillPeriodTo(request.billPeriodTo());
        entity.setGrossAmount(request.grossAmount());
        entity.setWbsPackageCode(request.wbsPackageCode());
        applyDeductions(entity, request);
        entity.setNetAmount(request.netAmount());
        entity.setContractorClaimedPercent(request.contractorClaimedPercent());
        entity.setRemarks(request.remarks());
        satelliteGateService.evaluate(entity);

        var saved = raBillRepository.save(entity);
        auditService.logUpdate("RaBill", raBillId, "raBill", null, RaBillDto.from(saved));
        return RaBillDto.from(saved);
    }

    // RA Bill Item Operations
    @Transactional
    public RaBillItemDto addRaBillItem(CreateRaBillItemRequest request) {
        var raBill = raBillRepository.findById(request.raBillId())
                .orElseThrow(() -> new ResourceNotFoundException("RaBill", request.raBillId()));

        var entity = new RaBillItem();
        entity.setRaBillId(request.raBillId());
        entity.setItemCode(request.itemCode());
        entity.setDescription(request.description());
        entity.setUnit(request.unit());
        entity.setRate(request.rate());
        entity.setPreviousQuantity(request.previousQuantity());
        entity.setCurrentQuantity(request.currentQuantity());
        entity.setCumulativeQuantity(request.cumulativeQuantity());
        entity.setAmount(request.amount());

        var saved = raBillItemRepository.save(entity);
        auditService.logCreate("RaBillItem", saved.getId(), RaBillItemDto.from(saved));
        return RaBillItemDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RaBillItemDto> getRaBillItems(UUID raBillId) {
        return raBillItemRepository.findByRaBillIdOrderByCreatedAt(raBillId)
                .stream()
                .map(RaBillItemDto::from)
                .collect(Collectors.toList());
    }

    // DPR Estimate Operations
    @Transactional
    public DprEstimateDto createDprEstimate(CreateDprEstimateRequest request) {
        var entity = new DprEstimate();
        entity.setProjectId(request.projectId());
        entity.setWbsNodeId(request.wbsNodeId());
        entity.setCostCategory(DprEstimate.CostCategory.valueOf(request.costCategory()));
        entity.setEstimatedAmount(request.estimatedAmount());
        entity.setRevisedAmount(request.revisedAmount());
        entity.setRemarks(request.remarks());

        var saved = dprEstimateRepository.save(entity);
        auditService.logCreate("DprEstimate", saved.getId(), DprEstimateDto.from(saved));
        return DprEstimateDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DprEstimateDto> getDprEstimatesByProject(UUID projectId) {
        return dprEstimateRepository.findByProjectIdOrderByCreatedAt(projectId)
                .stream()
                .map(DprEstimateDto::from)
                .collect(Collectors.toList());
    }

    // Cash Flow Forecast Operations
    @Transactional
    public CashFlowForecastDto createCashFlowForecast(CreateCashFlowForecastRequest request) {
        var entity = new CashFlowForecast();
        entity.setProjectId(request.projectId());
        entity.setPeriod(request.period());
        entity.setPlannedAmount(request.plannedAmount() != null ? request.plannedAmount() : java.math.BigDecimal.ZERO);
        entity.setActualAmount(request.actualAmount() != null ? request.actualAmount() : java.math.BigDecimal.ZERO);
        entity.setForecastAmount(request.forecastAmount() != null ? request.forecastAmount() : java.math.BigDecimal.ZERO);
        entity.setCumulativePlanned(request.cumulativePlanned());
        entity.setCumulativeActual(request.cumulativeActual());
        entity.setCumulativeForecast(request.cumulativeForecast());

        var saved = cashFlowForecastRepository.save(entity);
        auditService.logCreate("CashFlowForecast", saved.getId(), CashFlowForecastDto.from(saved));
        return CashFlowForecastDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CashFlowForecastDto> getCashFlowForecastByProject(UUID projectId) {
        return cashFlowForecastRepository.findByProjectIdOrderByPeriodAsc(projectId)
                .stream()
                .map(CashFlowForecastDto::from)
                .collect(Collectors.toList());
    }

    // Cost Summary
    @Transactional(readOnly = true)
    public CostSummaryDto getCostSummary(UUID projectId) {
        var expenses = activityExpenseRepository.findByProjectId(projectId);

        BigDecimal totalBudget = expenses.stream()
                .map(e -> e.getBudgetedCost() != null ? e.getBudgetedCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActual = expenses.stream()
                .map(e -> e.getActualCost() != null ? e.getActualCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemaining = expenses.stream()
                .map(e -> e.getRemainingCost() != null ? e.getRemainingCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal atCompletion = expenses.stream()
                .map(e -> e.getAtCompletionCost() != null ? e.getAtCompletionCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // PMS MasterData: pull material procurement + stock value so the summary reflects the
        // procurement ledger even when it hasn't been copied into ActivityExpense rows.
        BigDecimal materialProcurement = goodsReceiptNoteRepository
                .findByProjectIdOrderByReceivedDateDesc(projectId).stream()
                .map(g -> g.getAmount() != null ? g.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openStock = materialStockRepository.findByProjectId(projectId).stream()
                .map(s -> s.getStockValue() != null ? s.getStockValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materialIssued = materialProcurement.subtract(openStock);
        if (materialIssued.signum() < 0) materialIssued = BigDecimal.ZERO;

        return CostSummaryDto.of(totalBudget, totalActual, totalRemaining, atCompletion,
            expenses.size(), materialProcurement, openStock, materialIssued);
    }

    // Period Aggregation
    @Transactional(readOnly = true)
    public List<PeriodCostAggregationDto> aggregateByPeriod(UUID projectId) {
        var periods = financialPeriodRepository.findAllByOrderBySortOrder();
        var expenses = activityExpenseRepository.findByProjectId(projectId);
        var performances = storePeriodPerformanceRepository.findByProjectId(projectId);

        return periods.stream().map(period -> {
            // Sum actuals for expenses whose actual start date falls in this period
            BigDecimal periodBudget = BigDecimal.ZERO;
            BigDecimal periodActual = BigDecimal.ZERO;
            for (var expense : expenses) {
                if (expense.getActualStartDate() != null
                        && !expense.getActualStartDate().isBefore(period.getStartDate())
                        && !expense.getActualStartDate().isAfter(period.getEndDate())) {
                    periodActual = periodActual.add(
                            expense.getActualCost() != null ? expense.getActualCost() : BigDecimal.ZERO);
                    periodBudget = periodBudget.add(
                            expense.getBudgetedCost() != null ? expense.getBudgetedCost() : BigDecimal.ZERO);
                }
            }

            // EV/PV from StorePeriodPerformance
            BigDecimal ev = BigDecimal.ZERO;
            BigDecimal pv = BigDecimal.ZERO;
            for (var perf : performances) {
                if (period.getId().equals(perf.getFinancialPeriodId())) {
                    ev = ev.add(perf.getEarnedValueCost() != null ? perf.getEarnedValueCost() : BigDecimal.ZERO);
                    pv = pv.add(perf.getPlannedValueCost() != null ? perf.getPlannedValueCost() : BigDecimal.ZERO);
                }
            }

            BigDecimal variance = periodBudget.subtract(periodActual);

            return new PeriodCostAggregationDto(
                    period.getId(), period.getName(),
                    period.getStartDate(), period.getEndDate(),
                    periodBudget, periodActual, variance,
                    ev, pv
            );
        }).collect(Collectors.toList());
    }

    // Forecast Generation
    @Transactional(readOnly = true)
    public List<CashFlowForecastDto> generateForecast(UUID projectId, CashFlowForecastEngine.ForecastMethod method) {
        var periods = financialPeriodRepository.findAllByOrderBySortOrder();
        var expenses = activityExpenseRepository.findByProjectId(projectId);
        var performances = storePeriodPerformanceRepository.findByProjectId(projectId);

        return cashFlowForecastEngine.generateForecast(projectId, periods, expenses, performances, method);
    }
}
