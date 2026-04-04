package com.bipros.integration.adapter.impl;

import com.bipros.integration.adapter.GemAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Profile("!production")
public class MockGemAdapter implements GemAdapter {

    @Override
    public GemOrderResult placeOrder(GemOrderRequest request) {
        return new GemOrderResult(
            true,
            "GEM-ORD-" + UUID.randomUUID(),
            "Order placed successfully on GeM portal"
        );
    }

    @Override
    public GemOrderStatus checkOrderStatus(String gemOrderNumber) {
        return new GemOrderStatus(
            gemOrderNumber,
            "CONFIRMED",
            LocalDate.now().minusDays(3),
            LocalDate.now().plusDays(7)
        );
    }
}
