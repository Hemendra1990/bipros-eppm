package com.bipros.portfolio.application.dto;

import java.util.UUID;

public record ProjectRankingResponse(
    UUID projectId,
    String projectName,
    Integer rank,
    Double weightedScore) {}
