package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.manpower.ManpowerCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ManpowerComplianceRepository extends JpaRepository<ManpowerCompliance, UUID> {}
