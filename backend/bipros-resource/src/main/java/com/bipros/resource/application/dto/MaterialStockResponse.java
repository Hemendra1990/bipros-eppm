package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialStock;
import com.bipros.resource.domain.model.StockStatusTag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaterialStockResponse(
    UUID id,
    UUID projectId,
    UUID materialId,
    String materialCode,
    String materialName,
    BigDecimal openingStock,
    BigDecimal receivedMonth,
    BigDecimal issuedMonth,
    BigDecimal currentStock,
    BigDecimal minStockLevel,
    BigDecimal reorderQuantity,
    BigDecimal stockValue,
    StockStatusTag stockStatusTag,
    UUID lastGrnId,
    LocalDate lastIssueDate,
    BigDecimal cumulativeConsumed,
    BigDecimal wastagePercent
) {
    public static MaterialStockResponse from(MaterialStock s, String code, String name,
                                              BigDecimal minStock, BigDecimal reorderQty) {
        return new MaterialStockResponse(
            s.getId(), s.getProjectId(), s.getMaterialId(), code, name,
            s.getOpeningStock(), s.getReceivedMonth(), s.getIssuedMonth(),
            s.getCurrentStock(), minStock, reorderQty, s.getStockValue(),
            s.getStockStatusTag(), s.getLastGrnId(), s.getLastIssueDate(),
            s.getCumulativeConsumed(), s.getWastagePercent()
        );
    }
}
