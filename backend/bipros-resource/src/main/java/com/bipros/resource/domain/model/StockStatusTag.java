package com.bipros.resource.domain.model;

/**
 * Colour band for {@link MaterialStock}. Used by PMS MasterData Screen 09b: green (≥ min),
 * amber (< min), red (< 30% of min).
 */
public enum StockStatusTag {
    OK,
    LOW,
    CRITICAL
}
