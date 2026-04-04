package com.bipros.integration.repository;

import com.bipros.integration.model.CpppTender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CpppTenderRepository extends JpaRepository<CpppTender, UUID> {
    Page<CpppTender> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Optional<CpppTender> findByCpppTenderNumber(String cpppTenderNumber);
}
