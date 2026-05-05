package com.bipros.udf.domain.repository;

import com.bipros.udf.domain.model.FormulaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormulaVersionRepository extends JpaRepository<FormulaVersion, UUID> {
    List<FormulaVersion> findByFormulaCodeAndProjectIdOrderByVersionNumberDesc(String formulaCode, UUID projectId);

    List<FormulaVersion> findByFormulaCodeOrderByVersionNumberDesc(String formulaCode);

    Optional<FormulaVersion> findTopByFormulaCodeAndProjectIdOrderByVersionNumberDesc(String formulaCode, UUID projectId);

    Optional<FormulaVersion> findTopByFormulaCodeAndProjectIdIsNullOrderByVersionNumberDesc(String formulaCode);

    long countByFormulaCodeAndProjectId(String formulaCode, UUID projectId);
}
