package com.bipros.project.application.service;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HseCalendarResolverTest {

    @Mock private ProjectRepository projectRepository;

    private HseCalendarResolver resolver;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new HseCalendarResolver(projectRepository);
    }

    private Project projectWithCalendar(UUID calendarId) {
        Project p = new Project();
        p.setCalendarId(calendarId);
        return p;
    }

    @Test
    void noCalendarOnProject_returns8() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(projectWithCalendar(null)));

        assertThat(resolver.resolveHoursPerDay(projectId)).isEqualByComparingTo("8");
    }

    @Test
    void calendarWithHours_returnsThoseHours() {
        UUID calId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(projectWithCalendar(calId)));
        when(projectRepository.findCalendarHoursPerDay(calId)).thenReturn(Optional.of(10.0));

        assertThat(resolver.resolveHoursPerDay(projectId)).isEqualByComparingTo("10");
    }

    @Test
    void calendarIdSetButRowMissing_returns8() {
        UUID calId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(projectWithCalendar(calId)));
        when(projectRepository.findCalendarHoursPerDay(calId)).thenReturn(Optional.empty());

        assertThat(resolver.resolveHoursPerDay(projectId)).isEqualByComparingTo("8");
    }

    @Test
    void projectNotFound_returns8() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThat(resolver.resolveHoursPerDay(projectId)).isEqualByComparingTo("8");
    }
}
