package com.bipros.integration.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface GemAdapter {

    /**
     * Place an order on GeM portal
     */
    GemOrderResult placeOrder(GemOrderRequest request);

    /**
     * Check the status of a GeM order
     */
    GemOrderStatus checkOrderStatus(String gemOrderNumber);

    record GemOrderRequest(
        String gemCatalogueId,
        String itemDescription,
        Integer quantity,
        BigDecimal unitPrice,
        String vendorGemId,
        LocalDate requiredDeliveryDate
    ) {}

    record GemOrderResult(
        boolean success,
        String gemOrderNumber,
        String message
    ) {}

    record GemOrderStatus(
        String gemOrderNumber,
        String status,
        LocalDate orderDate,
        LocalDate deliveryDate
    ) {}
}
