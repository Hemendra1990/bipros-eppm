package com.bipros.analytics.etl.event;

import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.DprIssueChangedEvent;
import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.repository.DprIssueRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pushes DPR-issue rows to ClickHouse {@code fact_dpr_issues_daily} after the OLTP write
 * commits. Two paths: bulk via {@link DprSubmittedEvent} (parent DPR save touched issues), and
 * single-row via {@link DprIssueChangedEvent} (PATCH/DELETE outside the DPR drawer).
 *
 * <p>Mirrors the existing {@link DprSubmittedListener} pattern: AFTER_COMMIT, fetch fresh rows,
 * dead-letter on failure, metric on the failure counter. {@link DprMutationType#DELETED}
 * shares the same known limitation as the other DPR fact tables — historical rows linger until
 * the nightly reaper sweeps them (FINAL queries dedupe by version but cannot tombstone).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DprIssueChangedListener {

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final DprIssueRepository issueRepository;
    private final MeterRegistry meterRegistry;

    /** Bulk path — DPR drawer save touched issues. Always re-emits the full current set. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDprSubmitted(DprSubmittedEvent event) {
        if (event.eventType() == DprMutationType.DELETED) {
            // See KNOWN LIMITATION on DprSubmittedListener.onDprSubmitted — no tombstone.
            return;
        }
        if (event.issueCount() == 0 && event.eventType() == DprMutationType.CREATED) {
            return; // optimisation: never any issues on create with zero count
        }
        try {
            List<DprIssue> issues = issueRepository.findByDprIdOrderByOpenedAtAsc(event.dprId());
            for (DprIssue i : issues) {
                upsert(i);
            }
        } catch (Exception e) {
            log.error("ETL failed for DprSubmittedEvent (issues): {}", event, e);
            meterRegistry.counter("bipros.analytics.etl.failures", "fact", "fact_dpr_issues_daily").increment();
            deadLetter.record("project.dpr_issues", "fact_dpr_issues_daily", event, e);
        }
    }

    /** Single-row PATCH/DELETE path. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDprIssueChanged(DprIssueChangedEvent event) {
        if (event.eventType() == DprMutationType.DELETED) {
            return; // historical row lingers — same limitation as the bulk path
        }
        try {
            DprIssue issue = issueRepository.findById(event.issueId()).orElse(null);
            if (issue == null) {
                log.warn("Issue not found for DprIssueChangedEvent: {}", event);
                return;
            }
            upsert(issue);
        } catch (Exception e) {
            log.error("ETL failed for DprIssueChangedEvent: {}", event, e);
            meterRegistry.counter("bipros.analytics.etl.failures", "fact", "fact_dpr_issues_daily").increment();
            deadLetter.record("project.dpr_issues", "fact_dpr_issues_daily", event, e);
        }
    }

    private void upsert(DprIssue i) {
        Double ageHours = null;
        if (i.getResolvedAt() != null && i.getOpenedAt() != null) {
            ageHours = Duration.between(i.getOpenedAt(), i.getResolvedAt()).toMillis() / 3_600_000.0;
        } else if (i.getOpenedAt() != null) {
            ageHours = Duration.between(i.getOpenedAt(), Instant.now()).toMillis() / 3_600_000.0;
        }
        etl.insertDprIssue(
                i.getProjectId(), i.getDprId(), i.getId(),
                i.getActivityId(), i.getActivityName(),
                i.getSupervisorResourceId(), i.getSupervisorName(),
                i.getAssignedToResourceId(), i.getAssignedToName(),
                i.getReportDate(), i.getOpenedAt(), i.getResolvedAt(), ageHours,
                i.getCategory() != null ? i.getCategory().name() : null,
                i.getSeverity() != null ? i.getSeverity().name() : null,
                i.getStatus() != null ? i.getStatus().name() : null,
                i.getTitle(), i.getDescription(),
                i.getChainageFromM() != null ? i.getChainageFromM().doubleValue() : null,
                i.getChainageToM() != null ? i.getChainageToM().doubleValue() : null);
    }
}
