package com.bipros.integration.adapter.civilid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Permissive mock — accepts any non-blank ID. Active in every profile except {@code production}. */
@Component
@Profile("!production")
@Slf4j
public class MockCivilIdVerifier implements CivilIdVerifier {

    @Override
    public CivilIdVerificationResult verify(String civilId, String nationality) {
        if (civilId == null || civilId.isBlank()) {
            return CivilIdVerificationResult.unverified("blank civil id");
        }
        log.debug("[MockCivilIdVerifier] verifying {} ({}) -> verified", civilId, nationality);
        return CivilIdVerificationResult.ok(null, nationality);
    }
}
