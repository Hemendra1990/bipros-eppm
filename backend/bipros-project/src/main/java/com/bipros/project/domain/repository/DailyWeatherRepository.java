package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DailyWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyWeatherRepository extends JpaRepository<DailyWeather, UUID> {

  List<DailyWeather> findByProjectIdOrderByLogDateAscIdAsc(UUID projectId);

  List<DailyWeather> findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);

  Optional<DailyWeather> findByProjectIdAndLogDate(UUID projectId, LocalDate logDate);
}
