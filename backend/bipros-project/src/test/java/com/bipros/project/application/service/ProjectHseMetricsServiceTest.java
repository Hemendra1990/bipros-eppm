package com.bipros.project.application.service;

import com.bipros.project.application.dto.UpdateProjectHseMetricsRequest;
import com.bipros.project.domain.model.ProjectHseMetrics;
import com.bipros.project.domain.repository.ProjectHseMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectHseMetricsServiceTest {

    @Mock private ProjectHseMetricsRepository repository;

    private ProjectHseMetricsService service;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectHseMetricsService(repository);
    }

    @Test
    void getOrDefault_absent_returnsZeroKm() {
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());

        var res = service.getOrDefault(projectId);

        assertThat(res.kmDistanceDriven()).isEqualByComparingTo("0");
    }

    @Test
    void upsert_persistsKm_thenReturnsIt() {
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectHseMetrics.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.upsert(projectId,
            new UpdateProjectHseMetricsRequest(new BigDecimal("15460000")));

        assertThat(res.kmDistanceDriven()).isEqualByComparingTo("15460000");

        ArgumentCaptor<ProjectHseMetrics> cap = ArgumentCaptor.forClass(ProjectHseMetrics.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(cap.getValue().getKmDistanceDriven()).isEqualByComparingTo("15460000");
    }

    @Test
    void upsert_nullKm_defaultsToZero() {
        when(repository.findByProjectId(projectId)).thenReturn(Optional.empty());
        when(repository.save(any(ProjectHseMetrics.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.upsert(projectId, new UpdateProjectHseMetricsRequest(null));

        assertThat(res.kmDistanceDriven()).isEqualByComparingTo("0");
    }

    @Test
    void upsert_existingRow_mutatesInPlace_doesNotCreateNewEntity() {
        // Arrange: an already-persisted row with a known id and old values
        UUID existingId = UUID.randomUUID();
        ProjectHseMetrics existing = ProjectHseMetrics.builder()
            .projectId(projectId)
            .kmDistanceDriven(new BigDecimal("5000"))
            .build();
        existing.setId(existingId);

        when(repository.findByProjectId(projectId)).thenReturn(Optional.of(existing));
        when(repository.save(any(ProjectHseMetrics.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: upsert with new values
        var res = service.upsert(projectId,
            new UpdateProjectHseMetricsRequest(new BigDecimal("9999")));

        // Assert: saved entity is the same instance (same id) with updated fields
        ArgumentCaptor<ProjectHseMetrics> cap = ArgumentCaptor.forClass(ProjectHseMetrics.class);
        verify(repository).save(cap.capture());
        ProjectHseMetrics saved = cap.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo(existingId);
        assertThat(saved.getKmDistanceDriven()).isEqualByComparingTo("9999");
        assertThat(res.kmDistanceDriven()).isEqualByComparingTo("9999");
    }
}
