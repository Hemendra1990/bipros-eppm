package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One material's availability position for the Material Availability report.
 *
 * <p>Store closing = opening + received − issued, computed cumulatively to the report end date;
 * when the storekeeper daily log carries an explicit closing stock for the material, the latest
 * log figure wins (the store is authoritative). Site balance = issued-to-date − approved-DPR
 * consumed-to-date. All figures are quantities in {@code unit} — no money.
 *
 * <p>Alert codes: {@code BELOW_MIN_STOCK}, {@code LOW_COVER}, {@code NEGATIVE_BALANCE}.
 */
public record MaterialBalanceRow(
    String materialKey,
    String materialName,
    String unit,
    BigDecimal receivedWindow,
    BigDecimal issuedWindow,
    BigDecimal consumedWindow,
    BigDecimal receivedToDate,
    BigDecimal issuedToDate,
    BigDecimal consumedToDate,
    BigDecimal storeClosing,
    BigDecimal siteBalance,
    BigDecimal minStockLevel,
    BigDecimal avgDailyConsumption,
    BigDecimal daysOfCover,
    List<String> alerts
) {}
