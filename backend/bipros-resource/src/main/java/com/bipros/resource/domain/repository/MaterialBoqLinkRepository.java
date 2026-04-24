package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialBoqLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialBoqLinkRepository extends JpaRepository<MaterialBoqLink, UUID> {
    List<MaterialBoqLink> findByMaterialId(UUID materialId);
    List<MaterialBoqLink> findByBoqItemId(UUID boqItemId);
    void deleteByMaterialId(UUID materialId);
}
