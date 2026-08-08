package com.bipros.api.dprreport;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DprAgentReportRepository extends JpaRepository<DprAgentReport, UUID> {
    /** Scheduler's last-run lookup — FAILED runs are excluded so they don't suppress a same-day retry. */
    Optional<DprAgentReport> findTopByProjectIdAndTriggerAndStatusNotOrderByGeneratedAtDesc(
            UUID projectId, String trigger, String status);
    Optional<DprAgentReport> findTopByProjectIdOrderByGeneratedAtDesc(UUID projectId);

    /** Backs the DPR Analyst's {@code list_dpr_reports} tool — "which report do you mean?" disambiguation. */
    List<DprAgentReport> findTop20ByProjectIdOrderByGeneratedAtDesc(UUID projectId);
}
