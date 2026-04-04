package com.bipros.cost.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.cost.application.dto.*;
import com.bipros.cost.domain.entity.*;
import com.bipros.cost.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void deleteCostAccount(UUID id) {
        var entity = costAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostAccount", id));

        var childrenCount = costAccountRepository.findByParentIdOrderBySortOrder(id).size();
        if (childrenCount > 0) {
            throw new BusinessRuleException("COST_ACCOUNT_HAS_CHILDREN",
                    "Cannot delete cost account with child accounts");
        }

        costAccountRepository.delete(entity);
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
        return FinancialPeriodDto.from(saved);
    }

    @Transactional
    public void deleteFinancialPeriod(UUID id) {
        var entity = financialPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinancialPeriod", id));
        financialPeriodRepository.delete(entity);
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
    }
}
