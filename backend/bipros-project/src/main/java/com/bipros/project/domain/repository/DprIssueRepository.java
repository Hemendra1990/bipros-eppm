package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.HseIncidentType;
import com.bipros.project.domain.model.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Overdue rows for the Act-by SLA job: still-open statuses whose due date is already past. */
    List<DprIssue> findByProjectIdAndStatusInAndDueDateBefore(
        UUID projectId, Collection<IssueStatus> statuses, LocalDate date);

    /** Lightweight issue rows (status + severity only) for aggregating live/open/critical counts.
     *  Returns [dprId (UUID), status (IssueStatus), severity (IssueSeverity)]. */
    @Query("select i.dprId, i.status, i.severity from DprIssue i where i.dprId in :ids")
    List<Object[]> findStatusSeverityByDprIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Count classified HSE incidents of a given type for the project, EXCLUDING one status
     * (pass {@link IssueStatus#CANCELLED}). Rows with a null {@code hseIncidentType} never match
     * {@code = :hseIncidentType}, so unclassified SAFETY issues are not counted.
     */
    long countByProjectIdAndHseIncidentTypeAndStatusNot(
        UUID projectId, HseIncidentType hseIncidentType, IssueStatus status);

    /**
     * Anchor date for the "without-LTI" figures: the latest {@code reportDate} of any LTI issue
     * for the project ({@link Optional#empty()} when none). Excludes {@code CANCELLED} issues so a
     * mistakenly-logged-then-cancelled LTI does NOT suppress the without-LTI streak — consistent with
     * the incident counts, which also exclude CANCELLED.
     */
    @Query("select max(i.reportDate) from DprIssue i "
        + "where i.projectId = :projectId "
        + "and i.hseIncidentType = com.bipros.project.domain.model.HseIncidentType.LTI "
        + "and i.status <> com.bipros.project.domain.model.IssueStatus.CANCELLED")
    Optional<LocalDate> findLastLtiDate(@Param("projectId") UUID projectId);
}
