package com.bipros.resource.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Rollup status for all mandatory lab tests on a {@link MaterialSource}. Derived by
 * {@code MaterialSourceService} from the linked {@link MaterialSourceLabTest} rows.
 */
public enum LabTestStatus {
    ALL_PASS,
    TESTS_PENDING,
    ONE_OR_MORE_FAIL;

    @JsonCreator
    public static LabTestStatus fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ALL_PASS", "PASS" -> ALL_PASS;
            case "TESTS_PENDING", "PENDING" -> TESTS_PENDING;
            case "ONE_OR_MORE_FAIL", "FAIL", "FAILED" -> ONE_OR_MORE_FAIL;
            default -> throw new IllegalArgumentException(
                "Unknown LabTestStatus '" + value
                    + "' (valid: ALL_PASS, TESTS_PENDING, ONE_OR_MORE_FAIL)");
        };
    }
}
