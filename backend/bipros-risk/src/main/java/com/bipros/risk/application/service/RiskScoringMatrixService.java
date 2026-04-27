package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.risk.application.dto.RiskScoringMatrixDto;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskScoringConfig;
import com.bipros.risk.domain.model.RiskScoringMatrix;
import com.bipros.risk.domain.repository.RiskScoringConfigRepository;
import com.bipros.risk.domain.repository.RiskScoringMatrixRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RiskScoringMatrixService {

    private final RiskScoringMatrixRepository matrixRepository;
    private final RiskScoringConfigRepository configRepository;
    private final ProjectAccessGuard projectAccess;

    /**
     * Look up the score for a given probability and derived impact from the project's matrix.
     */
    @Transactional(readOnly = true)
    public Integer lookupScore(UUID projectId, Integer probability, Integer derivedImpact) {
        if (probability == null || derivedImpact == null) return null;

        return matrixRepository.findByProjectIdAndProbabilityValueAndImpactValue(
                projectId, probability, derivedImpact)
                .map(RiskScoringMatrix::getScore)
                .orElseGet(() -> {
                    log.warn("No scoring matrix cell for project={} P={} I={}; falling back to P×I",
                            projectId, probability, derivedImpact);
                    return probability * derivedImpact;
                });
    }

    /**
     * Compute the derived impact from cost and schedule impacts using the project's scoring method.
     * Used by HIGHEST_IMPACT and AVERAGE_IMPACT methods.
     */
    @Transactional(readOnly = true)
    public int deriveImpact(UUID projectId, Integer impactCost, Integer impactSchedule) {
        RiskScoringConfig config = getConfig(projectId);
        return Risk.deriveImpact(impactCost, impactSchedule, config.getScoringMethod());
    }

    /**
     * P6-style composite score that respects all three scoring methods:
     * <ul>
     *   <li>{@code HIGHEST_IMPACT}: matrix score using max(costImpact, scheduleImpact)</li>
     *   <li>{@code AVERAGE_IMPACT}: matrix score using avg(costImpact, scheduleImpact)</li>
     *   <li>{@code AVERAGE_INDIVIDUAL}: avg(matrixScore(P, costImpact), matrixScore(P, scheduleImpact))</li>
     * </ul>
     * Returns null if probability is null or both impact values are null.
     */
    @Transactional(readOnly = true)
    public Integer computeCompositeScore(UUID projectId, Integer probability,
                                         Integer impactCost, Integer impactSchedule) {
        if (probability == null) return null;
        if (impactCost == null && impactSchedule == null) return null;

        RiskScoringConfig config = getConfig(projectId);
        if (config.getScoringMethod() == RiskScoringConfig.ScoringMethod.AVERAGE_INDIVIDUAL) {
            Integer costScore = (impactCost != null)
                ? lookupScore(projectId, probability, impactCost) : null;
            Integer schedScore = (impactSchedule != null)
                ? lookupScore(projectId, probability, impactSchedule) : null;
            if (costScore == null && schedScore == null) return null;
            if (costScore == null) return schedScore;
            if (schedScore == null) return costScore;
            return (costScore + schedScore) / 2;
        }
        int derivedImpact = Risk.deriveImpact(impactCost, impactSchedule, config.getScoringMethod());
        return lookupScore(projectId, probability, derivedImpact);
    }

    /**
     * Ensure a project has a scoring matrix. If not, seed it with defaults.
     */
    public void ensureMatrixExists(UUID projectId) {
        if (matrixRepository.findByProjectId(projectId).isEmpty()) {
            createDefaultMatrix(projectId);
        }
    }

    /**
     * Create the default P6-style scoring matrix for a project (probability × impact, both 1..5).
     * Probability 1 = Very Low, 5 = Very High; impact 1 = Very Low, 5 = Very High.
     * Score = probability × impact, ranged 1..25 to align with deriveRag thresholds.
     */
    public void createDefaultMatrix(UUID projectId) {
        log.info("Creating default scoring matrix for project {}", projectId);

        if (configRepository.findByProjectId(projectId).isEmpty()) {
            RiskScoringConfig config = new RiskScoringConfig();
            config.setProjectId(projectId);
            config.setScoringMethod(RiskScoringConfig.ScoringMethod.HIGHEST_IMPACT);
            config.setActive(true);
            configRepository.save(config);
        }

        List<RiskScoringMatrix> cells = new ArrayList<>(25);
        for (int p = 1; p <= 5; p++) {
            for (int i = 1; i <= 5; i++) {
                RiskScoringMatrix cell = new RiskScoringMatrix();
                cell.setProjectId(projectId);
                cell.setProbabilityValue(p);
                cell.setImpactValue(i);
                cell.setScore(p * i);
                cells.add(cell);
            }
        }
        matrixRepository.saveAll(cells);
        log.info("Created {} scoring matrix cells for project {}", cells.size(), projectId);
    }

    @Transactional(readOnly = true)
    public List<RiskScoringMatrixDto> getMatrix(UUID projectId) {
        projectAccess.requireRead(projectId);
        return matrixRepository.findByProjectId(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Update the matrix for a project. Replaces all cells. Validates each cell belongs to
     * the path projectId and uses probability/impact values in 1..5.
     */
    public List<RiskScoringMatrixDto> updateMatrix(UUID projectId, List<RiskScoringMatrixDto> cells) {
        projectAccess.requireEdit(projectId);
        if (cells == null || cells.isEmpty()) {
            throw new BusinessRuleException("MATRIX_EMPTY", "Matrix must contain at least one cell");
        }
        for (RiskScoringMatrixDto cell : cells) {
            if (cell.getProbabilityValue() == null || cell.getImpactValue() == null
                || cell.getScore() == null) {
                throw new BusinessRuleException("MATRIX_CELL_INCOMPLETE",
                    "Matrix cell missing probabilityValue, impactValue, or score");
            }
            if (cell.getProbabilityValue() < 1 || cell.getProbabilityValue() > 5
                || cell.getImpactValue() < 1 || cell.getImpactValue() > 5) {
                throw new BusinessRuleException("MATRIX_CELL_OUT_OF_RANGE",
                    "Probability and impact values must be in 1..5");
            }
            if (cell.getProjectId() != null && !cell.getProjectId().equals(projectId)) {
                throw new BusinessRuleException("MATRIX_CELL_PROJECT_MISMATCH",
                    "Matrix cell projectId does not match path projectId");
            }
        }
        matrixRepository.deleteByProjectId(projectId);

        List<RiskScoringMatrix> entities = cells.stream().map(cell -> {
            RiskScoringMatrix e = new RiskScoringMatrix();
            e.setProjectId(projectId);
            e.setProbabilityValue(cell.getProbabilityValue());
            e.setImpactValue(cell.getImpactValue());
            e.setScore(cell.getScore());
            return e;
        }).toList();

        return matrixRepository.saveAll(entities).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get the scoring configuration for a project. Creates a default config on first access —
     * not marked readOnly because it may write.
     */
    @Transactional
    public RiskScoringConfig getConfig(UUID projectId) {
        return configRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    RiskScoringConfig config = new RiskScoringConfig();
                    config.setProjectId(projectId);
                    config.setScoringMethod(RiskScoringConfig.ScoringMethod.HIGHEST_IMPACT);
                    config.setActive(true);
                    return configRepository.save(config);
                });
    }

    /**
     * Read the project's scoring config without auto-creating it. Returns null if absent.
     */
    @Transactional(readOnly = true)
    public RiskScoringConfig getConfigForRead(UUID projectId) {
        projectAccess.requireRead(projectId);
        return configRepository.findByProjectId(projectId).orElse(null);
    }

    /**
     * Update the scoring configuration for a project.
     */
    public RiskScoringConfig updateConfig(UUID projectId, RiskScoringConfig.ScoringMethod method) {
        projectAccess.requireEdit(projectId);
        if (method == null) {
            throw new BusinessRuleException("SCORING_METHOD_REQUIRED",
                "Scoring method is required");
        }
        RiskScoringConfig config = getConfig(projectId);
        config.setScoringMethod(method);
        return configRepository.save(config);
    }

    private RiskScoringMatrixDto toDto(RiskScoringMatrix cell) {
        return RiskScoringMatrixDto.builder()
                .id(cell.getId())
                .projectId(cell.getProjectId())
                .probabilityValue(cell.getProbabilityValue())
                .impactValue(cell.getImpactValue())
                .score(cell.getScore())
                .build();
    }
}
