package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitIsolationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitIsolationPointRepository extends JpaRepository<PermitIsolationPoint, UUID> {
    List<PermitIsolationPoint> findByPermitId(UUID permitId);
}
