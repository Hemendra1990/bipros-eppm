package com.bipros.udf.domain.repository;

import com.bipros.udf.domain.model.FormulaOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormulaOverrideRepository extends JpaRepository<FormulaOverride, UUID> {
    Optional<FormulaOverride> findByFormulaCodeAndProjectId(String formulaCode, UUID projectId);

    List<FormulaOverride> findByProjectId(UUID projectId);

    List<FormulaOverride> findByFormulaCode(String formulaCode);

    boolean existsByFormulaCodeAndProjectId(String formulaCode, UUID projectId);
}
