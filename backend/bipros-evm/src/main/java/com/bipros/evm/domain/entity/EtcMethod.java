package com.bipros.evm.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EtcMethod {
    MANUAL,
    CPI_BASED,
    SPI_BASED,
    CPI_SPI_COMPOSITE,
    MANAGEMENT_OVERRIDE;

    @JsonCreator
    public static EtcMethod fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "MANUAL" -> MANUAL;
            case "CPI_BASED", "BAC_MINUS_EV_OVER_CPI", "CPI" -> CPI_BASED;
            case "SPI_BASED", "SPI" -> SPI_BASED;
            case "CPI_SPI_COMPOSITE", "COMPOSITE", "CPI_SPI" -> CPI_SPI_COMPOSITE;
            case "MANAGEMENT_OVERRIDE", "OVERRIDE", "BAC_MINUS_EV" -> MANAGEMENT_OVERRIDE;
            default -> throw new IllegalArgumentException(
                "Unknown EtcMethod '" + value
                    + "' (valid: MANUAL, CPI_BASED, SPI_BASED, CPI_SPI_COMPOSITE, MANAGEMENT_OVERRIDE)");
        };
    }
}
