package com.bipros.integration.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PfmsAdapter {

    /**
     * Check the status of a fund transfer in PFMS
     */
    PfmsFundStatus checkFundStatus(String sanctionOrderNumber);

    /**
     * Initiate a payment through PFMS
     */
    PfmsPaymentResult initiatePayment(PfmsPaymentRequest request);

    record PfmsPaymentRequest(
        String sanctionOrderNumber,
        String beneficiaryName,
        BigDecimal amount,
        String purpose
    ) {}

    record PfmsFundStatus(
        String sanctionOrderNumber,
        String referenceNumber,
        BigDecimal amount,
        String status,
        LocalDate transferDate
    ) {}

    record PfmsPaymentResult(
        boolean success,
        String referenceNumber,
        String message
    ) {}
}
