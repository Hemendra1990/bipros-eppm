package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.WbsNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WbsNodeRepository extends JpaRepository<WbsNode, UUID> {

    Page<WbsNode> findByUpdatedAtAfter(Instant since, Pageable pageable);


    List<WbsNode> findByProjectIdAndParentIdIsNullOrderBySortOrder(UUID projectId);

    List<WbsNode> findByParentIdOrderBySortOrder(UUID parentId);

    List<WbsNode> findByProjectIdOrderBySortOrder(UUID projectId);

    Optional<WbsNode> findByCode(String code);

    boolean existsByCode(String code);
}
