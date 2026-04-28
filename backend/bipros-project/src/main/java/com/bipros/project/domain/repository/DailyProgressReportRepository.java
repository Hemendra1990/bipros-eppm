package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DailyProgressReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyProgressReportRepository extends JpaRepository<DailyProgressReport, UUID> {

  Page<DailyProgressReport> findByUpdatedAtAfter(Instant since, Pageable pageable);


  List<DailyProgressReport> findByProjectIdOrderByReportDateAscIdAsc(UUID projectId);

  List<DailyProgressReport> findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(
      UUID projectId, LocalDate from, LocalDate to);

  List<DailyProgressReport> findByProjectIdAndActivityNameIgnoreCaseOrderByReportDateAsc(
      UUID projectId, String activityName);
}
