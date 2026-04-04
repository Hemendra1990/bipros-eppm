package com.bipros.baseline.application.dto;

import java.util.List;

public record BaselineDetailResponse(
    BaselineResponse baseline,
    List<BaselineActivityResponse> activities) {}
