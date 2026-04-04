package com.bipros.reporting.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.reporting.application.dto.CreateKpiDefinitionRequest;
import com.bipros.reporting.application.dto.DashboardConfigDto;
import com.bipros.reporting.application.dto.KpiDefinitionDto;
import com.bipros.reporting.application.dto.KpiSnapshotDto;
import com.bipros.reporting.domain.model.DashboardConfig;
import com.bipros.reporting.domain.model.KpiDefinition;
import com.bipros.reporting.domain.model.KpiSnapshot;
import com.bipros.reporting.domain.repository.DashboardConfigRepository;
import com.bipros.reporting.domain.repository.KpiDefinitionRepository;
import com.bipros.reporting.domain.repository.KpiSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardConfigRepository dashboardConfigRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;

    @Transactional(readOnly = true)
    public DashboardConfigDto getDashboardByTier(String tier) {
        var dashboardTier = DashboardConfig.DashboardTier.valueOf(tier);
        var entity = dashboardConfigRepository.findByTier(dashboardTier)
                .orElseThrow(() -> new ResourceNotFoundException("DashboardConfig", tier));
        return DashboardConfigDto.from(entity);
    }

    @Transactional(readOnly = true)
    public List<KpiSnapshotDto> getKpisByTierAndProject(String tier, UUID projectId) {
        var dashboardTier = DashboardConfig.DashboardTier.valueOf(tier);
        var config = dashboardConfigRepository.findByTier(dashboardTier)
                .orElseThrow(() -> new ResourceNotFoundException("DashboardConfig", tier));

        if (projectId == null) {
            return List.of();
        }

        return kpiSnapshotRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(KpiSnapshotDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KpiDefinitionDto> getAllKpiDefinitions() {
        return kpiDefinitionRepository.findAll()
                .stream()
                .filter(k -> k.getIsActive() != null && k.getIsActive())
                .map(KpiDefinitionDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public KpiDefinitionDto createKpiDefinition(CreateKpiDefinitionRequest request) {
        if (kpiDefinitionRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("KPI_DUPLICATE_CODE",
                    "KPI definition with code " + request.code() + " already exists");
        }

        var entity = new KpiDefinition();
        entity.setName(request.name());
        entity.setCode(request.code());
        entity.setFormula(request.formula());
        entity.setUnit(request.unit());
        entity.setGreenThreshold(request.greenThreshold());
        entity.setAmberThreshold(request.amberThreshold());
        entity.setRedThreshold(request.redThreshold());
        entity.setModuleSource(request.moduleSource());
        entity.setIsActive(request.isActive());

        var saved = kpiDefinitionRepository.save(entity);
        return KpiDefinitionDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<KpiSnapshotDto> getProjectKpiSnapshots(UUID projectId) {
        return kpiSnapshotRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(KpiSnapshotDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<KpiSnapshotDto> calculateProjectKpis(UUID projectId) {
        var kpiDefinitions = kpiDefinitionRepository.findByIsActiveTrue();

        var snapshots = kpiDefinitions.stream()
                .map(kpiDef -> {
                    var snapshot = new KpiSnapshot();
                    snapshot.setKpiDefinitionId(kpiDef.getId());
                    snapshot.setProjectId(projectId);
                    snapshot.setCalculatedAt(Instant.now());
                    snapshot.setValue(calculateKpiValue(kpiDef, projectId));
                    snapshot.setStatus(determineKpiStatus(snapshot.getValue(), kpiDef));
                    return snapshot;
                })
                .collect(Collectors.toList());

        var saved = snapshots.stream()
                .map(kpiSnapshotRepository::save)
                .collect(Collectors.toList());

        return saved.stream()
                .map(KpiSnapshotDto::from)
                .collect(Collectors.toList());
    }

    private Double calculateKpiValue(KpiDefinition kpiDef, UUID projectId) {
        // Placeholder implementation - would calculate based on formula and module source
        // In production, this would aggregate data from cost, schedule, contract modules
        return 0.0;
    }

    private KpiSnapshot.KpiStatus determineKpiStatus(Double value, KpiDefinition kpiDef) {
        if (value == null) {
            return KpiSnapshot.KpiStatus.RED;
        }

        if (kpiDef.getGreenThreshold() != null && value >= kpiDef.getGreenThreshold()) {
            return KpiSnapshot.KpiStatus.GREEN;
        }

        if (kpiDef.getAmberThreshold() != null && value >= kpiDef.getAmberThreshold()) {
            return KpiSnapshot.KpiStatus.AMBER;
        }

        return KpiSnapshot.KpiStatus.RED;
    }
}
