package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DprIssueRepository extends JpaRepository<DprIssue, UUID> {

    List<DprIssue> findByDprIdOrderByOpenedAtAsc(UUID dprId);

    /** Batch fetch for the list endpoint — avoids N+1 across DPR rows. */
    List<DprIssue> findByDprIdIn(Collection<UUID> dprIds);

    long countByDprId(UUID dprId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByDprId(UUID dprId);

    /** Scope-safe lookup for the PATCH/DELETE endpoint. */
    Optional<DprIssue> findByIdAndProjectId(UUID id, UUID projectId);

    List<DprIssue> findByProjectIdOrderByOpenedAtDesc(UUID projectId);

    List<DprIssue> findByProjectIdAndStatusInOrderByOpenedAtDesc(UUID projectId, Collection<IssueStatus> statuses);

    List<DprIssue> findByProjectIdAndReportDateBetweenOrderByOpenedAtDesc(UUID projectId, LocalDate from, LocalDate to);
}
