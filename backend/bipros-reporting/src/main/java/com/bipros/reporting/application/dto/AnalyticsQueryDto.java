package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.AnalyticsQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsQueryDto {

  private UUID id;
  private String userId;
  private String queryText;
  private String queryType;
  private String responseText;
  private String responseData;
  private Long responseTimeMs;
  private Instant createdAt;

  public static AnalyticsQueryDto from(AnalyticsQuery query) {
    return AnalyticsQueryDto.builder()
        .id(query.getId())
        .userId(query.getUserId())
        .queryText(query.getQueryText())
        .queryType(query.getQueryType().toString())
        .responseText(query.getResponseText())
        .responseData(query.getResponseData())
        .responseTimeMs(query.getResponseTimeMs())
        .createdAt(query.getCreatedAt())
        .build();
  }
}
