package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DPR Intelligence agent. A deterministic {@link #gather} over a project's Daily Progress Reports
 * that surfaces two factual reporting/workflow gaps the numbers already prove:
 *
 * <ul>
 *   <li>{@code DPR_MISSING} — the latest submitted DPR is older than the reporting threshold
 *       (2 days), so progress/EVM dashboards are running on stale data;</li>
 *   <li>{@code APPROVAL_BOTTLENECK} — DPRs sitting in SUBMITTED state past the approval SLA window,
 *       mirroring the cutoff logic of {@code DprApprovalSlaEscalationJob} (SUBMITTED older than
 *       {@code SLA_HOURS} = a bottleneck).</li>
 * </ul>
 *
 * <p>Both are direct facts read from stored report dates / submission timestamps, so confidence is
 * high (0.95 / 0.90). {@code DPR_ANOMALY} is intentionally skipped — there is no cheap productivity
 * norm baseline reachable from this module to anchor an "outlier output" claim deterministically.
 *
 * <p>Data source: {@link DailyProgressReportRepository#findByProjectIdOrderByReportDateAscIdAsc}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DprIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "dpr_intelligence";
    private static final Duration TTL = Duration.ofDays(7);

    /** A submitted DPR older than this many days is a reporting gap (factual). */
    private static final int MISSING_THRESHOLD_DAYS = 2;
    /** Approval SLA window in hours — mirrors {@code DprSlaConfig.DEFAULT_HOURS} (the aggregator bean
     *  lives in bipros-api and can't be injected here, so the default is reused as a constant). */
    private static final int SLA_HOURS = 24;

    private final DailyProgressReportRepository dprRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "DPR Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();

        if (projectId == null) {
            return new GatherResult(snapshot, candidates);
        }

        List<DailyProgressReport> dprs = dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant slaCutoff = now.minus(Duration.ofHours(SLA_HOURS));

        // Single pass: find the latest submitted/approved report date, and collect DPRs stuck in
        // SUBMITTED past the SLA cutoff.
        LocalDate latestReported = null;
        int submittedReportCount = 0;
        List<DailyProgressReport> stuck = new ArrayList<>();
        for (DailyProgressReport d : dprs) {
            DprApprovalStatus st = d.getApprovalStatus();
            boolean reported = st == DprApprovalStatus.SUBMITTED || st == DprApprovalStatus.APPROVED;
            if (reported) {
                submittedReportCount++;
                LocalDate rd = d.getReportDate();
                if (rd != null && (latestReported == null || rd.isAfter(latestReported))) {
                    latestReported = rd;
                }
            }
            if (st == DprApprovalStatus.SUBMITTED
                    && d.getSubmittedAt() != null
                    && d.getSubmittedAt().isBefore(slaCutoff)) {
                stuck.add(d);
            }
        }

        snapshot.put("submittedReportCount", submittedReportCount);
        if (latestReported == null) {
            snapshot.putNull("latestReportDate");
        } else {
            snapshot.put("latestReportDate", latestReported.toString());
        }

        Instant validUntil = now.plus(TTL);

        // --- DPR_MISSING ---
        if (latestReported != null) {
            long gapDays = ChronoUnit.DAYS.between(latestReported, today);
            snapshot.put("reportGapDays", gapDays);
            if (gapDays > MISSING_THRESHOLD_DAYS) {
                candidates.add(dprMissing(projectId, latestReported, today, gapDays,
                        submittedReportCount, validUntil));
            }
        }

        // --- APPROVAL_BOTTLENECK ---
        snapshot.put("stuckAwaitingApproval", stuck.size());
        if (!stuck.isEmpty()) {
            DailyProgressReport oldest = stuck.stream()
                    .min(Comparator.comparing(DailyProgressReport::getSubmittedAt))
                    .orElse(stuck.get(0));
            long oldestHours = Duration.between(oldest.getSubmittedAt(), now).toHours();
            snapshot.put("oldestStuckHours", oldestHours);
            candidates.add(approvalBottleneck(projectId, stuck.size(), oldest, oldestHours, validUntil));
        }

        // Most-severe first for a stable, meaningful narration order.
        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft dprMissing(UUID projectId, LocalDate latestReported, LocalDate today,
                                         long gapDays, int submittedReportCount, Instant validUntil) {
        Severity severity = gapDays > 7 ? Severity.HIGH : gapDays > 4 ? Severity.MEDIUM : Severity.LOW;
        return new AgentFindingDraft(
                "DPR_MISSING",
                "PROJECT",
                severity,
                0.95,
                "Direct fact: latest submitted DPR dated " + latestReported + " vs run date " + today,
                "No DPR filed for " + gapDays + " days",
                "The most recent submitted daily progress report is dated " + latestReported + ", "
                        + gapDays + " days before the run date (" + today + "). No newer DPR has been submitted.",
                "Site supervisors have not filed or submitted daily progress reports for the intervening "
                        + "days — a reporting gap, not a calculation error.",
                "Progress, earned value, productivity and DBS rollups all read from DPRs; a " + gapDays
                        + "-day reporting gap leaves the EVM and cost dashboards stale and hides any slippage "
                        + "until reporting resumes.",
                "Follow up with the site supervisors to file the missing DPRs for the last " + gapDays
                        + " days, and confirm whether work actually stopped or reporting simply lapsed.",
                List.of(
                        EvidenceRef.metric("Latest submitted DPR", latestReported.toString()),
                        EvidenceRef.metric("Reporting gap", gapDays + " days"),
                        EvidenceRef.metric("Submitted DPRs on record", String.valueOf(submittedReportCount)),
                        EvidenceRef.entity("DPR log", "Open", "project", projectId,
                                "/projects/" + projectId + "/dpr")),
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft approvalBottleneck(UUID projectId, int stuckCount, DailyProgressReport oldest,
                                                 long oldestHours, Instant validUntil) {
        Severity severity = (oldestHours >= 3L * SLA_HOURS || stuckCount >= 5) ? Severity.HIGH
                : (oldestHours >= 2L * SLA_HOURS || stuckCount >= 3) ? Severity.MEDIUM
                : Severity.LOW;
        return new AgentFindingDraft(
                "APPROVAL_BOTTLENECK",
                "PROJECT",
                severity,
                0.90,
                "Count of DPRs in SUBMITTED state past the " + SLA_HOURS + "h approval SLA",
                stuckCount + " DPR" + (stuckCount == 1 ? "" : "s") + " stuck awaiting approval past the "
                        + SLA_HOURS + "h SLA",
                stuckCount + " daily progress report" + (stuckCount == 1 ? " has" : "s have")
                        + " been in SUBMITTED state beyond the " + SLA_HOURS + "-hour approval SLA; the oldest "
                        + "has waited " + oldestHours + "h (" + oldest.getSupervisorName() + " — "
                        + oldest.getActivityName() + ").",
                "Assigned approvers have not actioned the submitted DPRs within the SLA window — an "
                        + "approval-queue bottleneck, not a data error.",
                "Unapproved DPRs do not feed approved-only EVM and BOQ progress; a growing approval backlog "
                        + "delays earned-value recognition and can stall downstream billing and capacity rollups.",
                "Clear the approval queue: action or reassign the " + stuckCount + " overdue DPR"
                        + (stuckCount == 1 ? "" : "s") + ", starting with the oldest (" + oldestHours
                        + "h), and escalate to the approver's manager if still unactioned.",
                List.of(
                        EvidenceRef.metric("DPRs past SLA", String.valueOf(stuckCount)),
                        EvidenceRef.metric("SLA window", SLA_HOURS + "h"),
                        EvidenceRef.metric("Oldest wait", oldestHours + "h"),
                        EvidenceRef.entity("Oldest DPR",
                                oldest.getSupervisorName() + " / " + oldest.getActivityName(),
                                "dpr", oldest.getId(),
                                "/projects/" + projectId + "/dpr?focus=" + oldest.getId())),
                Map.of("PROJECT_MANAGER", List.of(), "SITE_MANAGER", List.of()),
                validUntil);
    }
}
