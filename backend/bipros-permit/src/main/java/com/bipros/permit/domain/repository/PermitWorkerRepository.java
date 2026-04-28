package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitWorkerRepository extends JpaRepository<PermitWorker, UUID> {
    List<PermitWorker> findByPermitId(UUID permitId);

    void deleteByPermitId(UUID permitId);
}
