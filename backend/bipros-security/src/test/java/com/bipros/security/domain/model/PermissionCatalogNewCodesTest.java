package com.bipros.security.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogNewCodesTest {

    @Test
    void hasAllNewPermissionCodes() {
        String[] expected = {
                "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "NCR.APPROVE",
                "DATA_QUALITY.READ", "DATA_QUALITY.AUDIT",
                "DPR.QC_ANNOTATE",
                "YIELD_VARIANCE.READ",
                "AI.WRITE"
        };
        for (String code : expected) {
            assertTrue(PermissionCatalog.isValid(code),
                    "Missing permission code: " + code);
        }
    }
}
