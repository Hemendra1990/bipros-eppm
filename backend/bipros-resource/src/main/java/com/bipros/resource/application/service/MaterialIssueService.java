package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateMaterialIssueRequest;
import com.bipros.resource.application.dto.MaterialIssueResponse;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialIssueService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final MaterialIssueRepository issueRepository;
    private final MaterialRepository materialRepository;
    private final MaterialStockRepository stockRepository;
    private final MaterialStockService stockService;
    private final MaterialConsumptionLogRepository consumptionLogRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MaterialIssueResponse> listByProject(UUID projectId) {
        return issueRepository.findByProjectIdOrderByIssueDateDesc(projectId).stream()
            .map(MaterialIssueResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MaterialIssueResponse> listByMaterial(UUID materialId) {
        return issueRepository.findByMaterialIdOrderByIssueDateDesc(materialId).stream()
            .map(MaterialIssueResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MaterialIssueResponse get(UUID id) {
        return MaterialIssueResponse.from(issueRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialIssue", id)));
    }

    public MaterialIssueResponse create(UUID projectId, CreateMaterialIssueRequest request) {
        Material material = materialRepository.findById(request.materialId())
            .orElseThrow(() -> new ResourceNotFoundException("Material", request.materialId()));
        if (!material.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("MATERIAL_PROJECT_MISMATCH",
                "Material " + material.getCode() + " belongs to a different project");
        }

        // Prevent issuing more than what's on hand.
        MaterialStock stock = stockRepository
            .findByProjectIdAndMaterialId(projectId, request.materialId())
            .orElse(null);
        if (stock != null && stock.getCurrentStock() != null
            && stock.getCurrentStock().compareTo(request.quantity()) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                "Cannot issue " + request.quantity() + " " + material.getUnit()
                    + " — only " + stock.getCurrentStock() + " on hand");
        }

        MaterialIssue issue = MaterialIssue.builder()
            .projectId(projectId)
            .challanNumber(generateChallanNumber(request.issueDate()))
            .materialId(request.materialId())
            .issueDate(request.issueDate())
            .quantity(request.quantity())
            .issuedToUserId(request.issuedToUserId())
            .stretchId(request.stretchId())
            .activityId(request.activityId())
            .wastageQuantity(request.wastageQuantity())
            .remarks(request.remarks())
            .build();

        // Capture the pre-issue stock so the consumption log records the correct opening balance.
        java.math.BigDecimal openingBeforeIssue = stock != null && stock.getCurrentStock() != null
            ? stock.getCurrentStock() : java.math.BigDecimal.ZERO;

        MaterialIssue saved = issueRepository.save(issue);
        stockService.recordIssue(saved);
        writeConsumptionLog(saved, material, openingBeforeIssue);
        auditService.logCreate("MaterialIssue", saved.getId(), MaterialIssueResponse.from(saved));
        return MaterialIssueResponse.from(saved);
    }

    /**
     * Bridge the issue into the daily-report {@link MaterialConsumptionLog} table so the Daily
     * Progress Report and Material Consumption report see it without having to be taught about
     * MaterialIssue separately. Aggregates multiple issues for the same material/day onto one row.
     */
    private void writeConsumptionLog(MaterialIssue issue, Material material,
                                      java.math.BigDecimal openingBeforeIssue) {
        if (issue.getQuantity() == null) return;
        // Find an existing consumption row for (project, material, day); build one if not.
        MaterialConsumptionLog existing = consumptionLogRepository
            .findByProjectIdAndResourceIdAndLogDate(
                issue.getProjectId(), material.getId(), issue.getIssueDate())
            .orElse(null);

        java.math.BigDecimal consumed = issue.getQuantity();
        if (existing != null) {
            existing.setConsumed(existing.getConsumed().add(consumed));
            existing.setClosingStock(existing.getOpeningStock()
                .add(existing.getReceived())
                .subtract(existing.getConsumed()));
            updateWastageFromIssue(existing, issue);
            consumptionLogRepository.save(existing);
            return;
        }

        MaterialConsumptionLog row = MaterialConsumptionLog.builder()
            .projectId(issue.getProjectId())
            .logDate(issue.getIssueDate())
            .resourceId(material.getId())
            .materialName(material.getName())
            .unit(material.getUnit() != null ? material.getUnit().name() : "NOS")
            .openingStock(openingBeforeIssue)
            .received(java.math.BigDecimal.ZERO)
            .consumed(consumed)
            .closingStock(openingBeforeIssue.subtract(consumed))
            .build();
        updateWastageFromIssue(row, issue);
        consumptionLogRepository.save(row);
    }

    private static void updateWastageFromIssue(MaterialConsumptionLog row, MaterialIssue issue) {
        if (issue.getWastageQuantity() == null || row.getConsumed() == null
            || row.getConsumed().signum() == 0) return;
        java.math.BigDecimal pct = issue.getWastageQuantity()
            .multiply(java.math.BigDecimal.valueOf(100))
            .divide(row.getConsumed(), 2, java.math.RoundingMode.HALF_UP);
        row.setWastagePercent(pct);
    }

    private String generateChallanNumber(LocalDate date) {
        String month = (date != null ? date : LocalDate.now()).format(MONTH_FORMAT);
        String like = "ISS-" + month + "-%";
        int suffixStart = "ISS-".length() + month.length() + 2;
        Integer max = issueRepository.findMaxSuffixForPrefix(like, suffixStart);
        int next = max == null ? 1 : max + 1;
        return String.format("ISS-%s-%04d", month, next);
    }
}
