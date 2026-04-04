package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.ScoringModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoringModelRepository extends JpaRepository<ScoringModel, UUID> {

  Optional<ScoringModel> findByIsDefaultTrue();
}
