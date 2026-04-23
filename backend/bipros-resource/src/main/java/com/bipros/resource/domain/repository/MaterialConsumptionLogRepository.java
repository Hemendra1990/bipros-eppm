package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialConsumptionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MaterialConsumptionLogRepository
    extends JpaRepository<MaterialConsumptionLog, UUID> {

  List<MaterialConsumptionLog> findByProjectIdOrderByLogDateAscIdAsc(UUID projectId);

  List<MaterialConsumptionLog> findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);
}
