package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.algorithm.MultipleFloatPathFinder.FloatPath;

import java.util.List;
import java.util.UUID;

public record FloatPathResponse(
    int pathNumber,
    List<UUID> activities,
    double totalFloat
) {

  public static FloatPathResponse from(FloatPath floatPath) {
    return new FloatPathResponse(floatPath.pathNumber(), floatPath.activities(), floatPath.totalFloat());
  }
}
