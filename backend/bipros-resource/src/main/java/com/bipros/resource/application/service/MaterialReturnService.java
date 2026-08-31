package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateMaterialReturnRequest;
import com.bipros.resource.application.dto.MaterialReturnResponse;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialReturn;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import com.bipros.resource.domain.repository.MaterialReturnRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Records material coming back from a custodian against the issue slip that gave it out.
 *
 * <p>A USABLE return is a real store movement and is bridged into BOTH registers the same way
 * {@link MaterialIssueService} bridges an issue, because each one governs something different:
 * {@link MaterialStock} gates re-issue (the INSUFFICIENT_STOCK guard reads it), while the daily
 * {@link MaterialConsumptionLog} drives the store closing balance on the Material Consumption
 * Report — {@code MaterialBalanceService} prefers the latest log closing over its own computed
 * figure, so a return that skipped the log would silently leave the reported balance unchanged.
 *
 * <p>A SCRAP return does neither: the material is gone. It only drains the custodian's holding,
 * which is what leaves it counted as wastage in {@code MaterialKpiService}.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialReturnService {

    private final MaterialReturnRepository returnRepository;
    private final MaterialIssueRepository issueRepository;
    private final MaterialRepository materialRepository;
    private final MaterialStockRepository stockRepository;
    private final MaterialStockService stockService;
    private final MaterialConsumptionLogRepository consumptionLogRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MaterialReturnResponse> listByProject(UUID projectId) {
        return returnRepository.findByProjectIdOrderByReturnDateDesc(projectId).stream()
            .map(MaterialReturnResponse::from).toList();
    }

    /** Quantity still outstanding on an issue slip = issued − already returned. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingFor(UUID issueId) {
        MaterialIssue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialIssue", issueId));
        return outstanding(issue);
    }

    public MaterialReturnResponse create(UUID projectId, UUID issueId,
                                         CreateMaterialReturnRequest request) {
        MaterialIssue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialIssue", issueId));
        if (!issue.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("MATERIAL_ISSUE_PROJECT_MISMATCH",
                "Challan " + issue.getChallanNumber() + " belongs to a different project");
        }
        Material material = materialRepository.findById(issue.getMaterialId())
            .orElseThrow(() -> new ResourceNotFoundException("Material", issue.getMaterialId()));

        BigDecimal outstanding = outstanding(issue);
        if (request.quantity().compareTo(outstanding) > 0) {
            throw new BusinessRuleException("MATERIAL_RETURN_EXCEEDS_ISSUE",
                "Cannot return " + request.quantity() + " " + material.getUnit()
                    + " — only " + outstanding + " outstanding on challan "
                    + issue.getChallanNumber());
        }
        if (request.returnDate().isBefore(issue.getIssueDate())) {
            throw new BusinessRuleException("MATERIAL_RETURN_BEFORE_ISSUE",
                "Return date cannot precede the issue date (" + issue.getIssueDate() + ")");
        }

        MaterialReturn saved = returnRepository.save(MaterialReturn.builder()
            .projectId(projectId)
            .materialIssueId(issueId)
            .materialId(issue.getMaterialId())
            .returnDate(request.returnDate())
            .quantity(request.quantity())
            .condition(request.condition())
            .returnedByUserId(issue.getIssuedToUserId())
            .receivedByUserId(request.receivedByUserId())
            .remarks(request.remarks())
            .build());

        if (saved.getCondition() == MaterialReturn.ReturnCondition.USABLE) {
            // Capture the pre-credit stock so the log row's opening balance is honest.
            BigDecimal openingBeforeReturn = stockRepository
                .findByProjectIdAndMaterialId(projectId, issue.getMaterialId())
                .map(MaterialStock::getCurrentStock)
                .orElse(BigDecimal.ZERO);
            stockService.recordReturn(saved);
            writeConsumptionLog(saved, material, openingBeforeReturn);
        }

        auditService.logCreate("MaterialReturn", saved.getId(), MaterialReturnResponse.from(saved));
        return MaterialReturnResponse.from(saved);
    }

    private BigDecimal outstanding(MaterialIssue issue) {
        BigDecimal issued = issue.getQuantity() != null ? issue.getQuantity() : BigDecimal.ZERO;
        BigDecimal returned = returnRepository.sumByMaterialIssueId(issue.getId());
        return issued.subtract(returned != null ? returned : BigDecimal.ZERO);
    }

    /**
     * Mirror of {@link MaterialIssueService}'s bridge, in the receipt direction: a usable return
     * is inward stock movement, so it lands in the day's log as {@code received}. Aggregates onto
     * an existing row for the same material/day rather than creating a second one.
     */
    private void writeConsumptionLog(MaterialReturn ret, Material material,
                                     BigDecimal openingBeforeReturn) {
        MaterialConsumptionLog existing = consumptionLogRepository
            .findByProjectIdAndResourceIdAndLogDate(
                ret.getProjectId(), material.getId(), ret.getReturnDate())
            .orElse(null);

        if (existing != null) {
            existing.setReceived(existing.getReceived().add(ret.getQuantity()));
            existing.setClosingStock(existing.getOpeningStock()
                .add(existing.getReceived())
                .subtract(existing.getConsumed()));
            consumptionLogRepository.save(existing);
            return;
        }

        consumptionLogRepository.save(MaterialConsumptionLog.builder()
            .projectId(ret.getProjectId())
            .logDate(ret.getReturnDate())
            .resourceId(material.getId())
            .materialName(material.getName())
            .unit(material.getUnit() != null ? material.getUnit() : "NOS")
            .openingStock(openingBeforeReturn)
            .received(ret.getQuantity())
            .consumed(BigDecimal.ZERO)
            .closingStock(openingBeforeReturn.add(ret.getQuantity()))
            .build());
    }
}
