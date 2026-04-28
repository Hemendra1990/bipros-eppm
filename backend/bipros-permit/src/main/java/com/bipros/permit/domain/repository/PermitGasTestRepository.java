package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitGasTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitGasTestRepository extends JpaRepository<PermitGasTest, UUID> {
    List<PermitGasTest> findByPermitIdOrderByTestedAtDesc(UUID permitId);
}
