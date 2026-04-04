package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.ScenarioType;

public record CreateScenarioRequest(
    String scenarioName,
    String description,
    ScenarioType scenarioType
) {}
