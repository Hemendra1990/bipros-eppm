package com.bipros.api.dprreport;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dpr_agent_report", schema = "ai",
       indexes = {@Index(name = "idx_dpr_agent_report_project", columnList = "project_id, generated_at")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DprAgentReport extends BaseEntity {
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "trigger_type", nullable = false, length = 20) private String trigger; // SCHEDULED | ON_DEMAND
    @Column(name = "window_from") private LocalDate windowFrom;
    @Column(name = "window_to") private LocalDate windowTo;
    @Column(name = "window_label", length = 120) private String windowLabel;
    @Column(name = "filters_json", columnDefinition = "TEXT") private String filtersJson;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "requested_by_user_id") private UUID requestedByUserId;
    @Column(name = "status", nullable = false, length = 20) private String status; // SUCCESS | FAILED | PARTIAL
    @Column(name = "summary", columnDefinition = "TEXT") private String summary;
    @Column(name = "insights_json", columnDefinition = "TEXT") private String insightsJson;
    @Column(name = "html_body", columnDefinition = "TEXT") private String htmlBody;
    @Column(name = "prompt_tokens") private Integer promptTokens;
    @Column(name = "completion_tokens") private Integer completionTokens;
    @Column(name = "delivered_to", columnDefinition = "TEXT") private String deliveredTo;
    @Column(name = "delivery_status", length = 40) private String deliveryStatus; // SENT | PREVIEW | SKIPPED | FAILED
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
}
