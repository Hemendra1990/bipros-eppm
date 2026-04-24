package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialStockRepository extends JpaRepository<MaterialStock, UUID> {

    Optional<MaterialStock> findByProjectIdAndMaterialId(UUID projectId, UUID materialId);
    List<MaterialStock> findByProjectId(UUID projectId);
}
