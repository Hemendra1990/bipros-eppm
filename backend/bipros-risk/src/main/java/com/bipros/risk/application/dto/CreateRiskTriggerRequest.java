package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskTrigger;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRiskTriggerRequest {
    @NotNull(message = "Risk ID is required")
    private UUID riskId;

    @NotBlank(message = "Trigger condition is required")
    private String triggerCondition;

    @NotNull(message = "Trigger type is required")
    private RiskTrigger.TriggerType triggerType;

    @NotNull(message = "Threshold value is required")
    private Double thresholdValue;

    @NotNull(message = "Escalation level is required")
    private RiskTrigger.EscalationLevel escalationLevel;

    private String notifyRoles;
}
