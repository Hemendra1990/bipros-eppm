package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.MaterialStockResponse;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.model.StockStatusTag;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import com.bipros.resource.domain.repository.MaterialStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Maintains the {@link MaterialStock} aggregate per (project, material). Called from
 * {@link GoodsReceiptService} and {@link MaterialIssueService} on each write.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialStockService {

    private final MaterialStockRepository stockRepository;
    private final MaterialRepository materialRepository;
    private final GoodsReceiptNoteRepository grnRepository;
    private final MaterialIssueRepository issueRepository;

    @Transactional(readOnly = true)
    public List<MaterialStockResponse> listByProject(UUID projectId) {
        return stockRepository.findByProjectId(projectId).stream()
            .map(this::hydrate)
            .toList();
    }

    @Transactional(readOnly = true)
    public MaterialStockResponse getForMaterial(UUID projectId, UUID materialId) {
        MaterialStock stock = stockRepository.findByProjectIdAndMaterialId(projectId, materialId)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialStock",
                projectId + "/" + materialId));
        return hydrate(stock);
    }

    /** Apply a GRN: += quantity, update received-month, last GRN, recompute tag + value. */
    public MaterialStock recordGrn(GoodsReceiptNote grn) {
        MaterialStock stock = getOrCreate(grn.getProjectId(), grn.getMaterialId());
        stock.setCurrentStock(zero(stock.getCurrentStock()).add(grn.getQuantity()));
        if (inCurrentMonth(grn.getReceivedDate())) {
            stock.setReceivedMonth(zero(stock.getReceivedMonth()).add(grn.getQuantity()));
        }
        stock.setLastGrnId(grn.getId());
        recomputeDerivedFields(stock, grn.getUnitRate());
        return stockRepository.save(stock);
    }

    /** Apply an Issue: -= quantity, update issued-month, cumulative-consumed, last issue date. */
    public MaterialStock recordIssue(MaterialIssue issue) {
        MaterialStock stock = getOrCreate(issue.getProjectId(), issue.getMaterialId());
        stock.setCurrentStock(zero(stock.getCurrentStock()).subtract(issue.getQuantity()));
        if (inCurrentMonth(issue.getIssueDate())) {
            stock.setIssuedMonth(zero(stock.getIssuedMonth()).add(issue.getQuantity()));
        }
        stock.setLastIssueDate(issue.getIssueDate());
        stock.setCumulativeConsumed(zero(stock.getCumulativeConsumed()).add(issue.getQuantity()));
        if (issue.getWastageQuantity() != null
            && issue.getQuantity() != null
            && issue.getQuantity().signum() > 0) {
            BigDecimal wastagePct = issue.getWastageQuantity()
                .multiply(BigDecimal.valueOf(100))
                .divide(issue.getQuantity(), 4, RoundingMode.HALF_UP);
            stock.setWastagePercent(wastagePct);
        }
        recomputeDerivedFields(stock, null);
        return stockRepository.save(stock);
    }

    /**
     * Recompute the denormalised derived fields on {@link MaterialStock}. {@code hintRate} is
     * optional — if supplied (e.g. from a fresh GRN unit rate) it becomes the new unit rate
     * for valuation; otherwise we derive a unit rate from the most recent GRN for the same
     * material so the stockValue stays consistent as quantity changes.
     */
    private void recomputeDerivedFields(MaterialStock stock, BigDecimal hintRate) {
        Material material = materialRepository.findById(stock.getMaterialId()).orElse(null);
        BigDecimal minStock = material != null && material.getMinStockLevel() != null
            ? material.getMinStockLevel() : BigDecimal.ZERO;
        BigDecimal currentStock = zero(stock.getCurrentStock());

        StockStatusTag tag;
        if (minStock.signum() == 0) {
            tag = StockStatusTag.OK;
        } else if (currentStock.compareTo(minStock.multiply(BigDecimal.valueOf(0.3))) < 0) {
            tag = StockStatusTag.CRITICAL;
        } else if (currentStock.compareTo(minStock) < 0) {
            tag = StockStatusTag.LOW;
        } else {
            tag = StockStatusTag.OK;
        }
        stock.setStockStatusTag(tag);

        // Determine the unit rate for stock valuation:
        //   1. hintRate passed in (from a GRN we just recorded).
        //   2. Latest GRN for (project, material) — keeps value fresh even after issues.
        //   3. Existing stockValue/previousQty as a last-resort pro-rata.
        BigDecimal unitRate = hintRate;
        if (unitRate == null) {
            unitRate = grnRepository.findByMaterialIdOrderByReceivedDateDesc(stock.getMaterialId())
                .stream()
                .filter(g -> g.getUnitRate() != null)
                .findFirst()
                .map(GoodsReceiptNote::getUnitRate)
                .orElse(null);
        }
        if (unitRate != null) {
            stock.setStockValue(currentStock.multiply(unitRate).setScale(2, RoundingMode.HALF_UP));
        } else if (stock.getStockValue() != null && currentStock.signum() == 0) {
            stock.setStockValue(BigDecimal.ZERO);
        }
    }

    private MaterialStock getOrCreate(UUID projectId, UUID materialId) {
        return stockRepository.findByProjectIdAndMaterialId(projectId, materialId)
            .orElseGet(() -> {
                MaterialStock ms = MaterialStock.builder()
                    .projectId(projectId)
                    .materialId(materialId)
                    .openingStock(BigDecimal.ZERO)
                    .receivedMonth(BigDecimal.ZERO)
                    .issuedMonth(BigDecimal.ZERO)
                    .currentStock(BigDecimal.ZERO)
                    .cumulativeConsumed(BigDecimal.ZERO)
                    .stockStatusTag(StockStatusTag.OK)
                    .build();
                return stockRepository.save(ms);
            });
    }

    private MaterialStockResponse hydrate(MaterialStock stock) {
        Material m = materialRepository.findById(stock.getMaterialId()).orElse(null);
        return MaterialStockResponse.from(
            stock,
            m != null ? m.getCode() : null,
            m != null ? m.getName() : null,
            m != null ? m.getMinStockLevel() : null,
            m != null ? m.getReorderQuantity() : null);
    }

    private static BigDecimal zero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static boolean inCurrentMonth(LocalDate date) {
        return date != null && YearMonth.from(date).equals(YearMonth.now());
    }
}
