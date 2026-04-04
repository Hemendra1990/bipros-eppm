package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.EpsNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EpsNodeRepository extends JpaRepository<EpsNode, UUID> {

    List<EpsNode> findByParentIdIsNullOrderBySortOrder();

    List<EpsNode> findByParentIdOrderBySortOrder(UUID parentId);

    boolean existsByCode(String code);
}
