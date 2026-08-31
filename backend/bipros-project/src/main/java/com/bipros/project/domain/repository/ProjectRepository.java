package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    List<Project> findByEpsNodeId(UUID epsNodeId);

    /** Number of projects (active or archived) assigned to an OBS node — used to guard OBS deletion. */
    long countByObsNodeId(UUID obsNodeId);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    boolean existsByCode(String code);

    Optional<Project> findByCode(String code);

    /**
     * All non-archived projects. Use this from reporting / dashboard rollups so that archived
     * (soft-deleted) projects don't leak into portfolio scorecards, EVM aggregates, RAG bands,
     * etc. Plain {@link #findAll()} still returns archived rows too — leave it for code paths
     * that legitimately need the full history (e.g. boot-time backfills).
     */
    List<Project> findAllByArchivedAtIsNull();

    /**
     * Native cross-schema read of a calendar's standard hours-per-day, keyed by calendar id.
     * Returns empty when the calendar row does not exist. Lives here (rather than depending on
     * bipros-calendar) to keep this domain module free of cross-module coupling; all schemas
     * share one database, so the native read against {@code scheduling.calendars} is safe.
     */
    @Query(value = "SELECT c.standard_work_hours_per_day FROM scheduling.calendars c WHERE c.id = :calendarId",
        nativeQuery = true)
    Optional<Double> findCalendarHoursPerDay(@Param("calendarId") UUID calendarId);
}
