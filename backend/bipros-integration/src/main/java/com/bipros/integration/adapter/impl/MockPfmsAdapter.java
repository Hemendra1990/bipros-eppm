package com.bipros.integration.adapter.impl;

import com.bipros.integration.adapter.PfmsAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Profile("!production")
public class MockPfmsAdapter implements PfmsAdapter {

    @Override
    public PfmsFundStatus checkFundStatus(String sanctionOrderNumber) {
        return new PfmsFundStatus(
            sanctionOrderNumber,
            "PFMS-REF-" + UUID.randomUUID(),
            new BigDecimal("500000.00"),
            "APPROVED",
            LocalDate.now().minusDays(5)
        );
    }

    @Override
    public PfmsPaymentResult initiatePayment(PfmsPaymentRequest request) {
        return new PfmsPaymentResult(
            true,
            "PFMS-PAYMENT-" + UUID.randomUUID(),
            "Payment initiated successfully"
        );
    }
}
