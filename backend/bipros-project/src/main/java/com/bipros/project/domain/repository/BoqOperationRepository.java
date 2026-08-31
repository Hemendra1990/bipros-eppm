package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.BoqOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoqOperationRepository extends JpaRepository<BoqOperation, UUID> {

  List<BoqOperation> findByBoqItemIdOrderBySortOrderAscIdAsc(UUID boqItemId);

  boolean existsByBoqItemId(UUID boqItemId);

  long countByBoqItemId(UUID boqItemId);

  Optional<BoqOperation> findByIdAndBoqItemId(UUID id, UUID boqItemId);
}
