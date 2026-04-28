package com.bipros.permit.domain.repository;

import com.bipros.permit.domain.model.PermitCodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Sequence rows are advanced atomically via {@code INSERT ... ON CONFLICT ... RETURNING} in
 * {@link com.bipros.permit.application.service.PermitCodeAllocator}; this repository exists only
 * to register the entity and provide CRUD if ever needed.
 */
@Repository
public interface PermitCodeSequenceRepository extends JpaRepository<PermitCodeSequence, Integer> {
}
