package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaselineRepository extends JpaRepository<Baseline, UUID> {

  List<Baseline> findByProjectId(UUID projectId);

  List<Baseline> findByProjectIdAndBaselineType(UUID projectId, BaselineType baselineType);

  List<Baseline> findByProjectIdAndIsActiveTrue(UUID projectId);

  long countByProjectId(UUID projectId);
}
