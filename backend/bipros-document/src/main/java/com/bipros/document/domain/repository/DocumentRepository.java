package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByProjectId(UUID projectId);

    List<Document> findByFolderId(UUID folderId);

    Optional<Document> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<Document> findByDocumentNumber(String documentNumber);
}
