package com.bipros.resource.application.dto;

/**
 * Composite envelope for the six Manpower sub-tables. Each section is independently nullable —
 * callers may PUT just the {@code financials} block, for instance, leaving the others untouched.
 */
public record ManpowerDto(
    ManpowerMasterDto master,
    ManpowerSkillsDto skills,
    ManpowerFinancialsDto financials,
    ManpowerAttendanceDto attendance,
    ManpowerAllocationDto allocation,
    ManpowerComplianceDto compliance
) {}
