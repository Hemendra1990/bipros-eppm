package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.CorridorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorridorCodeRepository extends JpaRepository<CorridorCode, UUID> {

    Optional<CorridorCode> findByProjectId(UUID projectId);

    Optional<CorridorCode> findByGeneratedCode(String generatedCode);
}
