package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.MaterialIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, UUID> {

    List<MaterialIssue> findByMaterialIdOrderByIssueDateDesc(UUID materialId);
    List<MaterialIssue> findByProjectIdOrderByIssueDateDesc(UUID projectId);
    Optional<MaterialIssue> findByChallanNumber(String challanNumber);
    List<MaterialIssue> findByProjectIdAndIssueDateBetween(UUID projectId, LocalDate from, LocalDate to);

    @Query("select max(cast(substring(i.challanNumber, ?2) as integer)) "
        + "from MaterialIssue i where i.challanNumber like ?1")
    Integer findMaxSuffixForPrefix(String likePattern, int suffixStart);
}
