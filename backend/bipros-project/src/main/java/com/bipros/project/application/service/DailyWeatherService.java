package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyWeatherRequest;
import com.bipros.project.application.dto.DailyWeatherResponse;
import com.bipros.project.domain.model.DailyWeather;
import com.bipros.project.domain.repository.DailyWeatherRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyWeatherService {

  private final DailyWeatherRepository weatherRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  /**
   * Upsert-by-date: (project_id, log_date) carries a unique constraint, so a second POST for the
   * same day overwrites the existing row rather than failing on the DB constraint. This matches
   * supervisor workflow — they rewrite the day's weather readings as conditions evolve.
   */
  public DailyWeatherResponse create(UUID projectId, CreateDailyWeatherRequest request) {
    ensureProjectExists(projectId);

    Optional<DailyWeather> existing = weatherRepository.findByProjectIdAndLogDate(projectId, request.logDate());

    DailyWeather entity = existing.orElseGet(() ->
        DailyWeather.builder()
            .projectId(projectId)
            .logDate(request.logDate())
            .build());

    entity.setTempMaxC(request.tempMaxC());
    entity.setTempMinC(request.tempMinC());
    entity.setRainfallMm(request.rainfallMm());
    entity.setWindKmh(request.windKmh());
    entity.setWeatherCondition(request.weatherCondition());
    entity.setWorkingHours(request.workingHours());
    entity.setRemarks(request.remarks());

    DailyWeather saved = weatherRepository.save(entity);

    if (existing.isPresent()) {
      auditService.logUpdate("DailyWeather", saved.getId(), "weatherReading", null, DailyWeatherResponse.from(saved));
    } else {
      auditService.logCreate("DailyWeather", saved.getId(), DailyWeatherResponse.from(saved));
    }
    return DailyWeatherResponse.from(saved);
  }

  public List<DailyWeatherResponse> createBulk(UUID projectId, List<CreateDailyWeatherRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<DailyWeatherResponse> list(UUID projectId, LocalDate from, LocalDate to) {
    ensureProjectExists(projectId);
    List<DailyWeather> rows;
    if (from != null && to != null) {
      rows = weatherRepository.findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(projectId, from, to);
    } else {
      rows = weatherRepository.findByProjectIdOrderByLogDateAscIdAsc(projectId);
    }
    return rows.stream().map(DailyWeatherResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DailyWeatherResponse get(UUID projectId, UUID id) {
    return DailyWeatherResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    DailyWeather entity = find(projectId, id);
    weatherRepository.delete(entity);
    auditService.logDelete("DailyWeather", id);
  }

  private DailyWeather find(UUID projectId, UUID id) {
    DailyWeather entity = weatherRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyWeather", id));
    if (!entity.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyWeather", id);
    }
    return entity;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
