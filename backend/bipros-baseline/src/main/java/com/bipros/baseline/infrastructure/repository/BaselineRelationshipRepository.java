package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaselineRelationshipRepository extends JpaRepository<BaselineRelationship, UUID> {

  List<BaselineRelationship> findByBaselineId(UUID baselineId);
}
