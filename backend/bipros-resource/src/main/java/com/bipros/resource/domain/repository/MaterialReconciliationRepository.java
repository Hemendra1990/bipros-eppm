package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialReconciliationRepository extends JpaRepository<MaterialReconciliation, UUID> {

  List<MaterialReconciliation> findByProjectIdAndPeriod(UUID projectId, String period);

  List<MaterialReconciliation> findByResourceId(UUID resourceId);

  Optional<MaterialReconciliation> findByResourceIdAndPeriod(UUID resourceId, String period);

  List<MaterialReconciliation> findByProjectId(UUID projectId);

  @Query(
      "SELECT mr FROM MaterialReconciliation mr WHERE mr.resourceId = :resourceId ORDER BY mr.period DESC")
  List<MaterialReconciliation> findByResourceIdOrderByPeriodDesc(@Param("resourceId") UUID resourceId);
}
