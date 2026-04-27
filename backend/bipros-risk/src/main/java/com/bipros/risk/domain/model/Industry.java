package com.bipros.risk.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Broad industry tag used by the Risk Library and Risk Category Master to filter
 * categories down to ones that fit a project's domain. A risk category tagged
 * {@link #GENERIC} is shown for every project regardless of industry.
 */
public enum Industry {
    // Construction / infrastructure
    ROAD,
    BRIDGE,
    BUILDING,
    CONSTRUCTION_GENERAL,
    REFINERY,
    OIL_GAS,
    RAILWAY,
    METRO,
    POWER,
    WATER,
    MINING,

    // Manufacturing / process
    MANUFACTURING,
    PHARMA,

    // Services / IT / financial
    IT,
    TELECOM,
    BANKING_FINANCE,

    // People / public-domain projects
    HEALTHCARE,
    AGRICULTURE,
    AEROSPACE_DEFENSE,
    MARITIME,
    MASS_EVENT,

    // Cross-cutting (Schedule / Stakeholder / PMO / Force Majeure)
    GENERIC;

    @JsonCreator
    public static Industry fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "ROAD", "ROADS", "HIGHWAY_PROJECT" -> ROAD;
            case "BRIDGE", "BRIDGES" -> BRIDGE;
            case "BUILDING", "BUILDINGS" -> BUILDING;
            case "CONSTRUCTION", "CONSTRUCTION_GENERAL", "GENERAL_CONSTRUCTION" -> CONSTRUCTION_GENERAL;
            case "REFINERY", "REFINING" -> REFINERY;
            case "OIL_GAS", "OIL", "GAS", "OILGAS" -> OIL_GAS;
            case "RAILWAY", "RAIL" -> RAILWAY;
            case "METRO", "METRO_RAIL" -> METRO;
            case "POWER", "POWER_PLANT" -> POWER;
            case "WATER", "WATER_PROJECT" -> WATER;
            case "MINING", "MINES" -> MINING;
            case "MANUFACTURING", "FACTORY", "PLANT" -> MANUFACTURING;
            case "PHARMA", "PHARMACEUTICAL", "PHARMACEUTICALS" -> PHARMA;
            case "IT", "INFORMATION_TECHNOLOGY", "SOFTWARE" -> IT;
            case "TELECOM", "TELECOMMUNICATIONS" -> TELECOM;
            case "BANKING_FINANCE", "BANKING", "FINANCE", "BFSI" -> BANKING_FINANCE;
            case "HEALTHCARE", "HOSPITAL", "CLINICAL" -> HEALTHCARE;
            case "AGRICULTURE", "AGRI", "FARMING" -> AGRICULTURE;
            case "AEROSPACE_DEFENSE", "AEROSPACE", "DEFENSE", "DEFENCE" -> AEROSPACE_DEFENSE;
            case "MARITIME", "SHIPPING", "PORT" -> MARITIME;
            case "MASS_EVENT", "EVENT", "GATHERING", "KUMBH_MELA", "FESTIVAL", "SPORTS_EVENT" -> MASS_EVENT;
            case "GENERIC", "ANY", "OTHER" -> GENERIC;
            default -> throw new IllegalArgumentException(
                "Unknown Industry '" + value + "' (valid: ROAD, BRIDGE, BUILDING, "
                    + "CONSTRUCTION_GENERAL, REFINERY, OIL_GAS, RAILWAY, METRO, POWER, WATER, "
                    + "MINING, MANUFACTURING, PHARMA, IT, TELECOM, BANKING_FINANCE, HEALTHCARE, "
                    + "AGRICULTURE, AEROSPACE_DEFENSE, MARITIME, MASS_EVENT, GENERIC)");
        };
    }
}
