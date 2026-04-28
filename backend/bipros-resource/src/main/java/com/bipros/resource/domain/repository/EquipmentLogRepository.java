package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.EquipmentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EquipmentLogRepository extends JpaRepository<EquipmentLog, UUID> {

  Page<EquipmentLog> findByUpdatedAtAfter(Instant since, Pageable pageable);


  Page<EquipmentLog> findByResourceIdAndLogDateBetween(
      UUID resourceId,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable);

  Page<EquipmentLog> findByProjectIdAndLogDateBetween(
      UUID projectId,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable);

  List<EquipmentLog> findByProjectIdAndLogDateBetween(
      UUID projectId,
      LocalDate fromDate,
      LocalDate toDate);

  @Query(
      "SELECT SUM(el.operatingHours) FROM EquipmentLog el WHERE el.resourceId = :resourceId AND el.logDate BETWEEN :fromDate AND :toDate")
  Double sumOperatingHoursByResourceAndDateRange(
      @Param("resourceId") UUID resourceId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
