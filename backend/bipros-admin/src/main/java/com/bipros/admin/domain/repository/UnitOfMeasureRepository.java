package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {
    Optional<UnitOfMeasure> findByCode(String code);
}
