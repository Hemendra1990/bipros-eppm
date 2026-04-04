package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.DrawingRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DrawingRegisterRepository extends JpaRepository<DrawingRegister, UUID> {
    List<DrawingRegister> findByProjectId(UUID projectId);

    Optional<DrawingRegister> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<DrawingRegister> findByDrawingNumber(String drawingNumber);
}
