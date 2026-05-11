package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.ProjectCreatedEvent;
import com.bipros.common.event.ProjectUpdatedEvent;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDimensionListenerTest {

    private AnalyticsEtlService etl;
    private DeadLetterHandler deadLetter;
    private ProjectRepository projectRepository;
    private ProjectDimensionListener listener;

    @BeforeEach
    void setUp() {
        etl = mock(AnalyticsEtlService.class);
        deadLetter = mock(DeadLetterHandler.class);
        projectRepository = mock(ProjectRepository.class);
        listener = new ProjectDimensionListener(etl, deadLetter, projectRepository);
    }

    @Test
    void onProjectCreatedFetchesAndUpsertsProject() {
        UUID projectId = UUID.randomUUID();
        Project p = new Project();
        p.setId(projectId);
        p.setCode("PRJ-1");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        listener.onProjectCreated(new ProjectCreatedEvent(projectId, "PRJ-1", "Test"));

        verify(etl).upsertProjectDimension(p);
        verify(deadLetter, never()).record(anyString(), anyString(), any(), any());
    }

    @Test
    void onProjectUpdatedFetchesAndUpsertsProject() {
        UUID projectId = UUID.randomUUID();
        Project p = new Project();
        p.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        listener.onProjectUpdated(new ProjectUpdatedEvent(projectId, "PRJ-1", "Renamed"));

        verify(etl).upsertProjectDimension(p);
    }

    @Test
    void missingProjectIsSkippedSilently() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        listener.onProjectCreated(new ProjectCreatedEvent(projectId, "PRJ-1", "Test"));

        verify(etl, never()).upsertProjectDimension(any());
        verify(deadLetter, never()).record(anyString(), anyString(), any(), any());
    }

    @Test
    void etlExceptionRoutesToDeadLetter() {
        UUID projectId = UUID.randomUUID();
        Project p = new Project();
        p.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));
        doThrow(new RuntimeException("clickhouse down")).when(etl).upsertProjectDimension(p);

        ProjectCreatedEvent event = new ProjectCreatedEvent(projectId, "PRJ-1", "Test");
        listener.onProjectCreated(event);

        verify(deadLetter).record(
                eq("project.projects"),
                eq("dim_project"),
                eq(event),
                any(Exception.class));
    }
}
