package com.bipros.risk.domain.model;

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
import java.util.UUID;

@Entity
@Table(name = "risk_triggers", schema = "risk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskTrigger extends BaseEntity {

    @Column(nullable = false)
    private UUID riskId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String triggerCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    @Column(nullable = false)
    private Double thresholdValue;

    @Column
    private Double currentValue;

    @Column(nullable = false)
    private Boolean isTriggered = false;

    @Column
    private Instant triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationLevel escalationLevel = EscalationLevel.GREEN;

    @Column(length = 500)
    private String notifyRoles;

    public enum TriggerType {
        SCHEDULE_DELAY,
        COST_OVERRUN,
        MILESTONE_MISSED,
        MANUAL
    }

    public enum EscalationLevel {
        GREEN,
        AMBER,
        RED
    }
}
