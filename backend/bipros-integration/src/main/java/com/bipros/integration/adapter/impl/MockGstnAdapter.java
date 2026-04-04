package com.bipros.integration.adapter.impl;

import com.bipros.integration.adapter.GstnAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!production")
public class MockGstnAdapter implements GstnAdapter {

    @Override
    public GstnVerificationResult verifyGstin(String gstin) {
        if (gstin == null || gstin.length() != 15) {
            return new GstnVerificationResult(
                false,
                gstin,
                null,
                null,
                "INVALID",
                false,
                "Invalid GSTIN format"
            );
        }

        return new GstnVerificationResult(
            true,
            gstin,
            "ABC Construction Pvt Ltd",
            "ABC Constructions",
            "ACTIVE",
            true,
            "GSTIN verified successfully"
        );
    }
}
