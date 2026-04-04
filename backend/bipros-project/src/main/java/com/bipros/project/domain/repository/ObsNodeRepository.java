package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.ObsNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ObsNodeRepository extends JpaRepository<ObsNode, UUID> {

    List<ObsNode> findByParentIdIsNullOrderBySortOrder();

    List<ObsNode> findByParentIdOrderBySortOrder(UUID parentId);

    boolean existsByCode(String code);
}
