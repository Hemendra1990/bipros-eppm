package com.bipros.admin.domain.model;

/**
 * Organisation type per IC-PMS MasterData_OrgUsers taxonomy.
 * Used to scope user access and drive role-based UI labels (e.g. Employer vs SPV vs PMC).
 */
public enum OrganisationType {
    EMPLOYER,
    SPV,
    PMC,
    EPC_CONTRACTOR,
    GOVERNMENT_AUDITOR
}
