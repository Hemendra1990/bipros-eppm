package com.bipros.resource.application.dto;

public record ResourceHistogramEntry(
    String period,
    Double planned,
    Double actual
) {}
