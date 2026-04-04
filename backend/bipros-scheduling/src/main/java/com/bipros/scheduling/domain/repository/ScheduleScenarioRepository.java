package com.bipros.scheduling.domain.repository;

import com.bipros.scheduling.domain.model.ScheduleScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleScenarioRepository extends JpaRepository<ScheduleScenario, UUID> {

  List<ScheduleScenario> findByProjectId(UUID projectId);

  Optional<ScheduleScenario> findByProjectIdAndScenarioName(UUID projectId, String scenarioName);
}
