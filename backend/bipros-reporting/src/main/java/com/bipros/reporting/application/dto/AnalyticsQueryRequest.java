package com.bipros.reporting.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsQueryRequest {

  @NotBlank(message = "Query text is required")
  private String queryText;

  private UUID projectId;
}
