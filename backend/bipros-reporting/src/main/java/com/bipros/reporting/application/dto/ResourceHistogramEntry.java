package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceHistogramEntry(
    LocalDate date,
    Double plannedUnits,
    Double actualUnits,
    Double maxAvailable,
    Boolean isOverallocated) {}
