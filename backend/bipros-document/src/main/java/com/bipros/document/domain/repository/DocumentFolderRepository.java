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

    Optional<DocumentFolder> findByProjectIdAndId(UUID projectId, UUID id);
}
