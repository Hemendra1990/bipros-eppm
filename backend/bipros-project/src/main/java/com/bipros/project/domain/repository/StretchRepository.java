package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.Stretch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StretchRepository extends JpaRepository<Stretch, UUID> {

    List<Stretch> findByProjectIdOrderByFromChainageM(UUID projectId);

    boolean existsByProjectIdAndStretchCode(UUID projectId, String stretchCode);

    /** Maximum STR-NNN suffix used so the service can pick NNN+1 for the next auto-code. */
    @Query("select max(cast(substring(s.stretchCode, 5) as integer)) "
        + "from Stretch s where s.projectId = ?1 and s.stretchCode like 'STR-%' "
        + "and length(s.stretchCode) <= 10")
    Integer findMaxStretchSuffix(UUID projectId);
}
