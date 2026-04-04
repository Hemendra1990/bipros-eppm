package com.bipros.integration.repository;

import com.bipros.integration.model.GemOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GemOrderRepository extends JpaRepository<GemOrder, UUID> {
    Page<GemOrder> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Optional<GemOrder> findByGemOrderNumber(String gemOrderNumber);
}
