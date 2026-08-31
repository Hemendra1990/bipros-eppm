package com.bipros.gis.application.dto;

/** Immediate acknowledgement returned when an async ingestion run is dispatched. */
public record IngestionRunAck(java.util.UUID runId, String status) {}
