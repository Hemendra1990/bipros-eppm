package com.bipros.importexport.application.dto;

import java.util.List;

public record ApplySummary(
    int activitiesCreated,
    int activitiesUpdated,
    int wbsCreated,
    int wbsUpdated,
    int relationshipsCreated,
    int assignmentsUpserted,
    List<String> missingActivityCodes) {}
