package com.bipros.risk.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.risk.application.dto.CreateRiskTriggerRequest;
import com.bipros.risk.application.dto.RiskTriggerDto;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskTrigger;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskTriggerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RiskTriggerService {

    private final RiskTriggerRepository triggerRepository;
    private final RiskRepository riskRepository;
    private final AuditService auditService;

    public RiskTriggerDto createTrigger(UUID projectId, CreateRiskTriggerRequest request) {
        // Verify risk exists and belongs to project
        Risk risk = riskRepository.findById(request.getRiskId())
            .orElseThrow(() -> new ResourceNotFoundException("Risk", request.getRiskId()));

        if (!risk.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Risk", request.getRiskId());
        }

        RiskTrigger trigger = new RiskTrigger();
        trigger.setProjectId(projectId);
        trigger.setRiskId(request.getRiskId());
        trigger.setTriggerCondition(request.getTriggerCondition());
        trigger.setTriggerType(request.getTriggerType());
        trigger.setThresholdValue(request.getThresholdValue());
        trigger.setEscalationLevel(request.getEscalationLevel());
        trigger.setNotifyRoles(request.getNotifyRoles());
        trigger.setIsTriggered(false);

        RiskTrigger saved = triggerRepository.save(trigger);
        log.info("Risk trigger created: {} for risk: {}", saved.getId(), request.getRiskId());
        auditService.logCreate("RiskTrigger", saved.getId(), saved);

        return RiskTriggerDto.from(saved);
    }

    public RiskTriggerDto getTrigger(UUID triggerId) {
        RiskTrigger trigger = triggerRepository.findById(triggerId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTrigger", triggerId));
        return RiskTriggerDto.from(trigger);
    }

    public List<RiskTriggerDto> listProjectTriggers(UUID projectId) {
        List<RiskTrigger> triggers = triggerRepository.findByProjectId(projectId);
        return triggers.stream().map(RiskTriggerDto::from).collect(Collectors.toList());
    }

    public List<RiskTriggerDto> listTriggeredRisks(UUID projectId) {
        List<RiskTrigger> triggers = triggerRepository.findByProjectIdAndIsTriggeredTrue(projectId);
        return triggers.stream().map(RiskTriggerDto::from).collect(Collectors.toList());
    }

    public List<RiskTriggerDto> evaluateTriggers(UUID projectId) {
        List<RiskTrigger> triggers = triggerRepository.findByProjectId(projectId);

        for (RiskTrigger trigger : triggers) {
            // Placeholder evaluation logic
            // In production, integrate with actual project schedule and cost data
            evaluateTrigger(trigger);
        }

        triggerRepository.saveAll(triggers);
        return triggers.stream().map(RiskTriggerDto::from).collect(Collectors.toList());
    }

    public RiskTriggerDto updateTrigger(UUID triggerId, CreateRiskTriggerRequest request) {
        RiskTrigger trigger = triggerRepository.findById(triggerId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTrigger", triggerId));

        auditService.logUpdate("RiskTrigger", triggerId, "triggerCondition", trigger.getTriggerCondition(), request.getTriggerCondition());
        auditService.logUpdate("RiskTrigger", triggerId, "thresholdValue", trigger.getThresholdValue(), request.getThresholdValue());

        trigger.setTriggerCondition(request.getTriggerCondition());
        trigger.setTriggerType(request.getTriggerType());
        trigger.setThresholdValue(request.getThresholdValue());
        trigger.setEscalationLevel(request.getEscalationLevel());
        trigger.setNotifyRoles(request.getNotifyRoles());

        RiskTrigger updated = triggerRepository.save(trigger);
        return RiskTriggerDto.from(updated);
    }

    public void deleteTrigger(UUID triggerId) {
        RiskTrigger trigger = triggerRepository.findById(triggerId)
            .orElseThrow(() -> new ResourceNotFoundException("RiskTrigger", triggerId));
        triggerRepository.delete(trigger);
        log.info("Risk trigger deleted: {}", triggerId);
        auditService.logDelete("RiskTrigger", triggerId);
    }

    private void evaluateTrigger(RiskTrigger trigger) {
        // This is a placeholder implementation
        // In production, this would evaluate actual project metrics against trigger conditions
        // For example:
        // - If trigger type is SCHEDULE_DELAY, check actual schedule vs baseline
        // - If trigger type is COST_OVERRUN, check actual cost vs budget
        // - If trigger type is MILESTONE_MISSED, check milestone completion status

        // For now, we just set currentValue to threshold to show evaluation
        trigger.setCurrentValue(trigger.getThresholdValue());

        // Determine if triggered based on comparison
        // This logic would be more sophisticated in production
        boolean triggered = trigger.getCurrentValue() != null && trigger.getCurrentValue() >= trigger.getThresholdValue();

        if (triggered && !trigger.getIsTriggered()) {
            trigger.setIsTriggered(true);
            trigger.setTriggeredAt(Instant.now());
            log.warn("Risk trigger activated: {} for risk: {}", trigger.getId(), trigger.getRiskId());
        } else if (!triggered && trigger.getIsTriggered()) {
            trigger.setIsTriggered(false);
            trigger.setTriggeredAt(null);
        }
    }
}
