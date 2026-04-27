package com.bipros.api.config.seeder;

import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Translation helper for legacy seeders that historically passed a {@code RiskCategory}
 * enum constant. Maps the old enum NAMES (as strings) to the canonical master-row codes
 * created by {@link RiskCategorySeeder} / the Liquibase 036 backfill.
 *
 * <p>Each lookup is cached, and a missing code logs once and returns {@code null} so the
 * seeder still runs (the risk just ends up uncategorised — better than crashing boot).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LegacyRiskCategoryLookup {

    private final RiskCategoryMasterRepository repository;
    private final Map<String, RiskCategoryMaster> cache = new HashMap<>();

    /**
     * Map an old {@code RiskCategory} enum constant name → canonical category code.
     * Mirrors the SQL CASE expression in db/changelog/036-risk-category-master.yaml so
     * runtime lookups land on the same row as the migration backfill.
     */
    public static String codeForLegacyEnum(String legacyEnumName) {
        if (legacyEnumName == null) return null;
        return switch (legacyEnumName) {
            case "LAND_ACQUISITION"      -> "LA-GENERIC";
            case "FOREST_CLEARANCE"      -> "FE-STAGE-I-CLEARANCE";
            case "UTILITY_SHIFTING"      -> "US-GENERIC";
            case "STATUTORY_CLEARANCE"   -> "SR-GENERIC";
            case "CONTRACTOR_FINANCIAL"  -> "FIN-WORKING-CAPITAL-CRUNCH";
            case "MONSOON_IMPACT"        -> "MW-FLASH-FLOOD";
            case "GEOPOLITICAL"          -> "EG-GENERIC";
            case "NATURAL_HAZARD"        -> "FM-EARTHQUAKE";
            case "MARKET_PRICE"          -> "MP-BITUMEN-ESCALATION";
            case "TECHNOLOGY"            -> "DT-BIM-CLASH-LATE";
            case "HEALTH_SAFETY"         -> "HSE-GENERIC";
            case "TECHNICAL"             -> "DT-IRC-REVISION";
            case "EXTERNAL"              -> "EG-GENERIC";
            case "ORGANIZATIONAL"        -> "PMO-GENERIC";
            case "PROJECT_MANAGEMENT"    -> "PMO-CHANGE-CONTROL";
            case "SCHEDULE"              -> "SCH-CRITICAL-PATH";
            case "COST"                  -> "FIN-GENERIC";
            case "RESOURCE"              -> "RES-QUARRY-CLOSURE";
            case "QUALITY"               -> "CQ-GENERIC";
            default                      -> null;
        };
    }

    /** Returns the master row for an old enum name, or null if it can't be resolved. */
    public RiskCategoryMaster forLegacyEnum(String legacyEnumName) {
        if (legacyEnumName == null) return null;
        String code = codeForLegacyEnum(legacyEnumName);
        if (code == null) {
            log.warn("[LegacyRiskCategoryLookup] no mapping for legacy enum name '{}'", legacyEnumName);
            return null;
        }
        return byCode(code);
    }

    /** Returns the master row for a canonical category code, or null if not seeded. */
    public RiskCategoryMaster byCode(String code) {
        if (code == null || code.isBlank()) return null;
        RiskCategoryMaster cached = cache.get(code);
        if (cached != null) return cached;
        RiskCategoryMaster found = repository.findByCode(code).orElse(null);
        if (found == null) {
            log.warn("[LegacyRiskCategoryLookup] code '{}' not found in risk_category_master", code);
            return null;
        }
        cache.put(code, found);
        return found;
    }
}
