package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermitLifecycleEventRepository extends JpaRepository<PermitLifecycleEvent, UUID> {
    List<PermitLifecycleEvent> findByPermitIdOrderByOccurredAtDesc(UUID permitId);
}
