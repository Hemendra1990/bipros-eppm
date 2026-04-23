package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.NextDayPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface NextDayPlanRepository extends JpaRepository<NextDayPlan, UUID> {

  List<NextDayPlan> findByProjectIdOrderByReportDateAscIdAsc(UUID projectId);

  List<NextDayPlan> findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);
}
