package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.Transmittal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransmittalRepository extends JpaRepository<Transmittal, UUID> {
    List<Transmittal> findByProjectId(UUID projectId);

    Optional<Transmittal> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<Transmittal> findByTransmittalNumber(String transmittalNumber);
}
