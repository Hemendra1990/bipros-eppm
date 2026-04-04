package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "analytics_queries", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsQuery extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
  private String queryText;

  @Column(name = "query_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private QueryType queryType;

  @Column(name = "response_text", columnDefinition = "TEXT")
  private String responseText;

  @Column(name = "response_data", columnDefinition = "TEXT")
  private String responseData;

  @Column(name = "response_time_ms")
  private Long responseTimeMs;

  public enum QueryType {
    PROJECT_STATUS,
    COST_QUERY,
    SCHEDULE_QUERY,
    RISK_QUERY,
    RESOURCE_QUERY,
    GENERAL
  }
}
