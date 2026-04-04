package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.SkillCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LabourReturnRepository extends JpaRepository<LabourReturn, UUID> {

  Page<LabourReturn> findByProjectIdAndReturnDateBetween(
      UUID projectId,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable);

  List<LabourReturn> findByProjectIdAndReturnDateBetween(
      UUID projectId,
      LocalDate fromDate,
      LocalDate toDate);

  @Query(
      "SELECT SUM(lr.headCount) FROM LabourReturn lr WHERE lr.projectId = :projectId AND lr.returnDate = :returnDate")
  Integer getTotalHeadCountByDateAndProject(
      @Param("projectId") UUID projectId,
      @Param("returnDate") LocalDate returnDate);

  @Query(
      "SELECT lr.skillCategory, SUM(lr.headCount), SUM(lr.manDays) FROM LabourReturn lr WHERE lr.projectId = :projectId AND lr.returnDate BETWEEN :fromDate AND :toDate GROUP BY lr.skillCategory")
  List<Object[]> getSkillCategorySummary(
      @Param("projectId") UUID projectId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
