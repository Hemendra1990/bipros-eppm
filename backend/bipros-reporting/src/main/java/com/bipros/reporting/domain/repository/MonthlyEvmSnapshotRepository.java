package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.MonthlyEvmSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonthlyEvmSnapshotRepository extends JpaRepository<MonthlyEvmSnapshot, UUID> {

    List<MonthlyEvmSnapshot> findByProjectIdOrderByReportMonthDesc(UUID projectId);

    List<MonthlyEvmSnapshot> findByNodeCodeOrderByReportMonthDesc(String nodeCode);

    Optional<MonthlyEvmSnapshot> findByNodeIdAndReportMonth(UUID nodeId, LocalDate reportMonth);
}
