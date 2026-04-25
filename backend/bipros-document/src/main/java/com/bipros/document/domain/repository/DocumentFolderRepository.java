package com.bipros.document.domain.repository;

import com.bipros.document.domain.model.DocumentFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, UUID> {
    List<DocumentFolder> findByProjectIdOrderBySortOrder(UUID projectId);

    List<DocumentFolder> findByProjectIdAndParentIdOrderBySortOrder(UUID projectId, UUID parentId);

    /**
     * Returns the project's root folders (parentId IS NULL). Used both by the
     * Documents UI sidebar and by DefaultFolderSeeder's idempotency check.
     * Distinct from the {@code AndParentId} variant above because Spring Data
     * derived queries bind a null parameter as SQL {@code = NULL}, which never
     * matches; we need explicit {@code IS NULL}.
     */
    List<DocumentFolder> findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId);

    Optional<DocumentFolder> findByProjectIdAndId(UUID projectId, UUID id);
}
