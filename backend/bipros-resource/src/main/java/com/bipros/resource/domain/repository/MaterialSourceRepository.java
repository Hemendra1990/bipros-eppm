package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialSource;
import com.bipros.resource.domain.model.MaterialSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialSourceRepository extends JpaRepository<MaterialSource, UUID> {

    List<MaterialSource> findByProjectId(UUID projectId);

    List<MaterialSource> findByProjectIdAndSourceType(UUID projectId, MaterialSourceType sourceType);

    boolean existsByProjectIdAndSourceCode(UUID projectId, String sourceCode);

    /**
     * Greatest numeric suffix on existing {@code sourceCode} values that share the given prefix
     * ({@code BA-}, {@code QRY-}, {@code BD-}, {@code CEM-}) for the project. The service uses
     * the result + 1 when auto-generating the next code.
     */
    @Query("select max(cast(substring(s.sourceCode, ?3) as integer)) "
        + "from MaterialSource s where s.projectId = ?1 and s.sourceCode like ?2")
    Integer findMaxSuffix(UUID projectId, String likePattern, int suffixStart);
}
