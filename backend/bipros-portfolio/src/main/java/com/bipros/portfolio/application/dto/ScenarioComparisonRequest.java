package com.bipros.portfolio.application.dto;

import java.util.List;
import java.util.UUID;

public record ScenarioComparisonRequest(
    List<UUID> scenarioIds
) {}
