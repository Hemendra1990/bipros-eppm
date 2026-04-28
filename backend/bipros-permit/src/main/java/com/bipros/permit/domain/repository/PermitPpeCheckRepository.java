package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitPpeCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitPpeCheckRepository extends JpaRepository<PermitPpeCheck, UUID> {
    List<PermitPpeCheck> findByPermitId(UUID permitId);

    void deleteByPermitId(UUID permitId);
}
