package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialReturnRepository extends JpaRepository<MaterialReturn, UUID> {

    List<MaterialReturn> findByProjectId(UUID projectId);

    List<MaterialReturn> findByProjectIdOrderByReturnDateDesc(UUID projectId);

    List<MaterialReturn> findByMaterialIssueId(UUID materialIssueId);

    /** Quantity already returned against one issue slip — the outstanding-quantity guard. */
    @Query("select coalesce(sum(r.quantity), 0) from MaterialReturn r "
        + "where r.materialIssueId = :issueId")
    BigDecimal sumByMaterialIssueId(@Param("issueId") UUID issueId);
}
