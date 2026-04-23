package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DailyResourceDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyResourceDeploymentRepository extends JpaRepository<DailyResourceDeployment, UUID> {

  List<DailyResourceDeployment> findByProjectIdOrderByLogDateAscIdAsc(UUID projectId);

  List<DailyResourceDeployment> findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);
}
