package com.bipros.risk.domain.model;

public enum RiskImpact {
    VERY_LOW(1),
    LOW(2),
    MEDIUM(3),
    HIGH(4),
    VERY_HIGH(5);

    private final int value;

    RiskImpact(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
