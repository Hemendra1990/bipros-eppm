package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceCurve;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceCurveRepository extends JpaRepository<ResourceCurve, UUID> {

  List<ResourceCurve> findByIsDefaultTrue();

  Optional<ResourceCurve> findByName(String name);
}
