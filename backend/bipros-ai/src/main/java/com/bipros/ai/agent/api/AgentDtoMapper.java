package com.bipros.ai.agent.api;

import com.bipros.ai.agent.api.dto.AgentDtos.AgentFindingDto;
import com.bipros.ai.agent.api.dto.AgentDtos.AgentRunDto;
import com.bipros.ai.agent.api.dto.AgentDtos.EvidenceDto;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.ai.agent.memory.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps agent domain entities to API DTOs. Evidence JSON is decoded via {@link AgentMemoryService}. */
@Component
@RequiredArgsConstructor
public class AgentDtoMapper {

    private final AgentMemoryService memory;

    public AgentRunDto toRunDto(AgentRun r) {
        if (r == null) return null;
        return new AgentRunDto(
                r.getId(), r.getAgentKey(), r.getProjectId(), r.getPipelineRunId(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getTriggerType(), r.getTriggerRef(),
                r.getTokensInput(), r.getTokensOutput(),
                r.getLlmSkipReason() == null ? null : r.getLlmSkipReason().name(),
                r.getFindingsCount(), r.getStartedAt(), r.getFinishedAt(), r.getDurationMs(), r.getErrorMessage());
    }

    public AgentFindingDto toFindingDto(AgentFinding f) {
        List<EvidenceDto> evidence = memory.readEvidence(f).stream().map(this::toEvidenceDto).toList();
        return new AgentFindingDto(
                f.getId(), f.getAgentKey(), f.getProjectId(), f.getFindingType(), f.getSubjectRef(),
                f.getSeverity() == null ? null : f.getSeverity().name(),
                f.getConfidence(), f.getConfidenceBasis(), f.getTitle(),
                f.getWhatHappened(), f.getWhyItHappened(), f.getBusinessImpact(), f.getRecommendedAction(),
                evidence,
                f.getStatus() == null ? null : f.getStatus().name(),
                f.isNotifiable(), f.getValidUntil(), f.getLastSeenAt(),
                f.getAcknowledgedBy(), f.getAcknowledgedAt(), f.getResolvedBy(), f.getResolvedAt(), f.getCreatedAt());
    }

    private EvidenceDto toEvidenceDto(EvidenceRef e) {
        return new EvidenceDto(e.type(), e.label(), e.value(), e.entityType(), e.entityId(), e.linkUrl(), e.series());
    }
}
