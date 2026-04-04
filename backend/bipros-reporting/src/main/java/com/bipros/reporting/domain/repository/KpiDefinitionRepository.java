package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.KpiDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KpiDefinitionRepository extends JpaRepository<KpiDefinition, UUID> {
    Optional<KpiDefinition> findByCode(String code);
    List<KpiDefinition> findByIsActiveTrue();
}
