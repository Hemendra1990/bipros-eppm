package com.bipros.scheduling.domain.repository;

import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleHealthIndexRepository extends JpaRepository<ScheduleHealthIndex, UUID> {
  Optional<ScheduleHealthIndex> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);

  Optional<ScheduleHealthIndex> findByScheduleResultId(UUID scheduleResultId);
}
