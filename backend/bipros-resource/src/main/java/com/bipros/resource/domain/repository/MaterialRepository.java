package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByProjectId(UUID projectId);

    List<Material> findByProjectIdAndCategory(UUID projectId, MaterialCategory category);

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    @Query("select max(cast(substring(m.code, 5) as integer)) "
        + "from Material m where m.projectId = ?1 and m.code like 'MAT-%' "
        + "and length(m.code) <= 10")
    Integer findMaxSuffix(UUID projectId);
}
