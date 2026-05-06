package com.bipros.udf.domain.repository;

import com.bipros.udf.domain.model.FormulaCategory;
import com.bipros.udf.domain.model.FormulaMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormulaMasterRepository extends JpaRepository<FormulaMaster, UUID> {
    Optional<FormulaMaster> findByCode(String code);

    List<FormulaMaster> findByCategory(FormulaCategory category);

    List<FormulaMaster> findByIsActiveTrue();

    boolean existsByCode(String code);
}
