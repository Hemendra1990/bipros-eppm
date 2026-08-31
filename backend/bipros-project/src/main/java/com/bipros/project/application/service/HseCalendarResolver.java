package com.bipros.project.application.service;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resolves the effective per-person standard hours-per-day for a project's HSE man-hours fallback:
 * the project's assigned Calendar {@code standard_work_hours_per_day}, defaulting to 8 when the
 * project has no calendar, the calendar row is missing, or the project is not found. Used only as
 * the fallback when a DPR manpower row logged no working hours.
 */
@Component
@RequiredArgsConstructor
public class HseCalendarResolver {

    static final BigDecimal DEFAULT_HOURS_PER_DAY = BigDecimal.valueOf(8);

    private final ProjectRepository projectRepository;

    public BigDecimal resolveHoursPerDay(UUID projectId) {
        UUID calendarId = projectRepository.findById(projectId)
            .map(Project::getCalendarId)
            .orElse(null);
        if (calendarId == null) {
            return DEFAULT_HOURS_PER_DAY;
        }
        return projectRepository.findCalendarHoursPerDay(calendarId)
            .map(BigDecimal::valueOf)
            .orElse(DEFAULT_HOURS_PER_DAY);
    }
}
