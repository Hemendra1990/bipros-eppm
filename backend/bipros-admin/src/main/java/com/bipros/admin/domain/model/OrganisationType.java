package com.bipros.admin.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Organisation type — combines the IC-PMS MasterData_OrgUsers taxonomy (Employer / SPV / PMC
 * / EPC Contractor / Government Auditor) with the contractor-centric types required by the
 * PMS MasterData UI Screen 02 (Main Contractor / Sub-Contractor / Consultant / IE / Supplier).
 */
public enum OrganisationType {
    EMPLOYER,
    SPV,
    PMC,
    EPC_CONTRACTOR,
    GOVERNMENT_AUDITOR,
    MAIN_CONTRACTOR,
    SUB_CONTRACTOR,
    CONSULTANT,
    IE,
    SUPPLIER;

    @JsonCreator
    public static OrganisationType fromString(String value) {
        if (value == null) return null;
        String n = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (n) {
            case "EMPLOYER", "CLIENT" -> EMPLOYER;
            case "SPV" -> SPV;
            case "PMC" -> PMC;
            case "EPC_CONTRACTOR", "EPC" -> EPC_CONTRACTOR;
            case "GOVERNMENT_AUDITOR", "AUDITOR" -> GOVERNMENT_AUDITOR;
            case "MAIN_CONTRACTOR", "MAIN" -> MAIN_CONTRACTOR;
            case "SUB_CONTRACTOR", "SUB" -> SUB_CONTRACTOR;
            case "CONSULTANT" -> CONSULTANT;
            case "IE", "INDEPENDENT_ENGINEER" -> IE;
            case "SUPPLIER", "VENDOR" -> SUPPLIER;
            default -> throw new IllegalArgumentException(
                "Unknown OrganisationType '" + value
                    + "' (valid: EMPLOYER, SPV, PMC, EPC_CONTRACTOR, GOVERNMENT_AUDITOR, MAIN_CONTRACTOR, SUB_CONTRACTOR, CONSULTANT, IE, SUPPLIER)");
        };
    }
}
