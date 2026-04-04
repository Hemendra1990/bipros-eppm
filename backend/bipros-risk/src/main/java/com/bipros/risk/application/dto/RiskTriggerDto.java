package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskTrigger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskTriggerDto {
    private UUID id;
    private UUID riskId;
    private UUID projectId;
    private String triggerCondition;
    private String triggerType;
    private Double thresholdValue;
    private Double currentValue;
    private Boolean isTriggered;
    private Instant triggeredAt;
    private String escalationLevel;
    private String notifyRoles;
    private Instant createdAt;

    public static RiskTriggerDto from(RiskTrigger trigger) {
        return new RiskTriggerDto(
            trigger.getId(),
            trigger.getRiskId(),
            trigger.getProjectId(),
            trigger.getTriggerCondition(),
            trigger.getTriggerType().toString(),
            trigger.getThresholdValue(),
            trigger.getCurrentValue(),
            trigger.getIsTriggered(),
            trigger.getTriggeredAt(),
            trigger.getEscalationLevel().toString(),
            trigger.getNotifyRoles(),
            trigger.getCreatedAt()
        );
    }
}
