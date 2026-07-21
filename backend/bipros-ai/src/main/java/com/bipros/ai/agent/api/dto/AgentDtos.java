package com.bipros.ai.agent.api.dto;

import com.bipros.ai.agent.core.EvidenceRef;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTOs for the agent API. Grouped in one file since they are small, cohesive records. */
public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentSummaryDto(
            String key,
            String displayName,
            boolean supportsPortfolio,
            AgentRunDto lastRun,
            /** Currently-ACTIVE findings for this agent on this project — the persistent count the
             *  KPI cards sum. Stays correct across a SKIPPED_NO_CHANGE run (unlike lastRun.findingsCount,
             *  which is 0 on a skip). */
            int activeFindingsCount) {
    }

    public record AgentRunDto(
            UUID id,
            String agentKey,
            UUID projectId,
            UUID pipelineRunId,
            String status,
            String triggerType,
            String triggerRef,
            Integer tokensInput,
            Integer tokensOutput,
            String llmSkipReason,
            int findingsCount,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String errorMessage,
            JsonNode snapshot) {
    }

    public record AgentRunDetailDto(
            AgentRunDto run,
            List<AgentFindingDto> findings) {
    }

    public record EvidenceDto(
            String type,
            String label,
            String value,
            String entityType,
            UUID entityId,
            String linkUrl,
            EvidenceRef.Series series,
            Double numericValue,
            String unit) {
    }

    public record AgentFindingDto(
            UUID id,
            String agentKey,
            UUID projectId,
            String findingType,
            String subjectRef,
            String severity,
            double confidence,
            String confidenceBasis,
            String title,
            String whatHappened,
            String whyItHappened,
            String businessImpact,
            String recommendedAction,
            List<EvidenceDto> evidence,
            String status,
            boolean notifiable,
            Instant validUntil,
            Instant lastSeenAt,
            UUID acknowledgedBy,
            String acknowledgedByName,
            Instant acknowledgedAt,
            UUID resolvedBy,
            String resolvedByName,
            Instant resolvedAt,
            Instant createdAt) {
    }

    public record RunAcceptedResponse(UUID runId) {
    }

    public record PipelineRunAcceptedResponse(UUID pipelineRunId) {
    }

    /** Minimal page envelope (avoids serialising Spring's Page directly). */
    public record PageDto<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
