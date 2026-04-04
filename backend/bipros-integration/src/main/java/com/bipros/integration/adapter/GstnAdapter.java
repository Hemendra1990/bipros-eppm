package com.bipros.integration.adapter;

public interface GstnAdapter {

    /**
     * Verify a GSTIN with GSTN
     */
    GstnVerificationResult verifyGstin(String gstin);

    record GstnVerificationResult(
        boolean success,
        String gstin,
        String legalName,
        String tradeName,
        String gstStatus,
        boolean isActive,
        String message
    ) {}
}
