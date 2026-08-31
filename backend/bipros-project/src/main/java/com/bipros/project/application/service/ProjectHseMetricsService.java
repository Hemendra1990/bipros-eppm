package com.bipros.project.application.service;

import com.bipros.project.application.dto.ProjectHseMetricsResponse;
import com.bipros.project.application.dto.UpdateProjectHseMetricsRequest;
import com.bipros.project.domain.model.ProjectHseMetrics;
import com.bipros.project.domain.repository.ProjectHseMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read/upsert of the per-project manual HSE inputs (KM driven).
 * {@link #getOrDefault} synthesises a zero-KM response when no row exists yet, so the HSE tab
 * renders before the safety officer has entered anything.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectHseMetricsService {

    private final ProjectHseMetricsRepository repository;

    @Transactional(readOnly = true)
    public ProjectHseMetricsResponse getOrDefault(UUID projectId) {
        return repository.findByProjectId(projectId)
            .map(ProjectHseMetricsResponse::from)
            .orElseGet(() -> new ProjectHseMetricsResponse(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public ProjectHseMetricsResponse upsert(UUID projectId, UpdateProjectHseMetricsRequest req) {
        ProjectHseMetrics entity = repository.findByProjectId(projectId)
            .orElseGet(() -> ProjectHseMetrics.builder().projectId(projectId).build());
        entity.setKmDistanceDriven(
            req.kmDistanceDriven() != null ? req.kmDistanceDriven() : BigDecimal.ZERO);
        entity.setIndirectManHours(
            req.indirectManHours() != null ? req.indirectManHours() : BigDecimal.ZERO);
        return ProjectHseMetricsResponse.from(repository.save(entity));
    }
}
