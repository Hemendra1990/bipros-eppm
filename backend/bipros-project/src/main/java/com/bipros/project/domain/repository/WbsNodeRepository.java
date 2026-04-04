package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.WbsNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WbsNodeRepository extends JpaRepository<WbsNode, UUID> {

    List<WbsNode> findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId);

    List<WbsNode> findByParentIdOrderBySortOrder(UUID parentId);

    List<WbsNode> findByProjectIdOrderBySortOrder(UUID projectId);
}
