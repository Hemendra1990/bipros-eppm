package com.bipros.contract.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * IC-PMS contract types — FIDIC colour variants + legacy forms as per MasterData sheet M5
 * plus the NHAI/MoRTH contract families listed on the Project Master screen
 * (EPC / BOT / HAM / Item Rate / Lump Sum / Annuity).
 */
public enum ContractType {
    EPC_LUMP_SUM_FIDIC_YELLOW,
    EPC_LUMP_SUM_FIDIC_RED,
    EPC_LUMP_SUM_FIDIC_SILVER,
    ITEM_RATE_FIDIC_RED,
    PERCENTAGE_BASED_PMC,
    LUMP_SUM_UNIT_RATE,
    EPC,
    BOT,
    HAM,
    ITEM_RATE,
    LUMP_SUM,
    ANNUITY;

    @JsonCreator
    public static ContractType fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "EPC_LUMP_SUM_FIDIC_YELLOW", "FIDIC_YELLOW" -> EPC_LUMP_SUM_FIDIC_YELLOW;
            case "EPC_LUMP_SUM_FIDIC_RED", "FIDIC_RED" -> EPC_LUMP_SUM_FIDIC_RED;
            case "EPC_LUMP_SUM_FIDIC_SILVER", "FIDIC_SILVER" -> EPC_LUMP_SUM_FIDIC_SILVER;
            case "ITEM_RATE_FIDIC_RED" -> ITEM_RATE_FIDIC_RED;
            case "PERCENTAGE_BASED_PMC", "PMC" -> PERCENTAGE_BASED_PMC;
            case "LUMP_SUM_UNIT_RATE" -> LUMP_SUM_UNIT_RATE;
            case "EPC", "ENGINEERING_PROCUREMENT_CONSTRUCTION" -> EPC;
            case "BOT", "BUILD_OPERATE_TRANSFER" -> BOT;
            case "HAM", "HYBRID_ANNUITY_MODEL", "HYBRID_ANNUITY" -> HAM;
            case "ITEM_RATE" -> ITEM_RATE;
            case "LUMP_SUM" -> LUMP_SUM;
            case "ANNUITY" -> ANNUITY;
            default -> throw new IllegalArgumentException(
                "Unknown ContractType '" + value
                    + "' (valid: EPC, BOT, HAM, ITEM_RATE, LUMP_SUM, ANNUITY, EPC_LUMP_SUM_FIDIC_YELLOW/RED/SILVER, ITEM_RATE_FIDIC_RED, PERCENTAGE_BASED_PMC, LUMP_SUM_UNIT_RATE)");
        };
    }
}
