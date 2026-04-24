package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.GoodsReceiptNote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoodsReceiptResponse(
    UUID id,
    UUID projectId,
    String grnNumber,
    UUID materialId,
    LocalDate receivedDate,
    BigDecimal quantity,
    BigDecimal unitRate,
    BigDecimal amount,
    UUID supplierOrganisationId,
    String poNumber,
    String vehicleNumber,
    UUID receivedByUserId,
    BigDecimal acceptedQuantity,
    BigDecimal rejectedQuantity,
    String remarks
) {
    public static GoodsReceiptResponse from(GoodsReceiptNote g) {
        return new GoodsReceiptResponse(
            g.getId(), g.getProjectId(), g.getGrnNumber(), g.getMaterialId(),
            g.getReceivedDate(), g.getQuantity(), g.getUnitRate(), g.getAmount(),
            g.getSupplierOrganisationId(), g.getPoNumber(), g.getVehicleNumber(),
            g.getReceivedByUserId(), g.getAcceptedQuantity(), g.getRejectedQuantity(),
            g.getRemarks()
        );
    }
}
