package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.EpsNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EpsNodeRepository extends JpaRepository<EpsNode, UUID> {

    List<EpsNode> findByParentIdIsNullOrderBySortOrder();

    List<EpsNode> findByParentIdOrderBySortOrder(UUID parentId);

    boolean existsByCode(String code);

    @Query("""
        select n from EpsNode n
        where lower(n.name) like lower(concat('%', :q, '%'))
           or lower(n.code) like lower(concat('%', :q, '%'))
    """)
    Page<EpsNode> searchByCodeOrName(@Param("q") String q, Pageable pageable);
}
