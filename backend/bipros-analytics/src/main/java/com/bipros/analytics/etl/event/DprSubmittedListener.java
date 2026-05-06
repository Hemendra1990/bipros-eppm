package com.bipros.analytics.etl.event;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.etl.AnalyticsEtlService;
import com.bipros.analytics.etl.DeadLetterHandler;
import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DprSubmittedListener {

    // Control tokens used by common LLM tokenizers — strip before storing to prevent injection via remarks
    private static final Pattern CONTROL_TOKEN_PATTERN = Pattern.compile(
            "<\\|im_start\\|>|<\\|im_end\\|>|<\\|endoftext\\|>|<\\|fim_prefix\\|>|<\\|fim_middle\\|>|<\\|fim_suffix\\|>|<\\|fim_pad\\|>|<\\|startoftext\\|>",
            Pattern.CASE_INSENSITIVE);

    private static String sanitizeRemarks(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String stripped = CONTROL_TOKEN_PATTERN.matcher(raw).replaceAll("");
        return "<UNTRUSTED_DATA>" + stripped + "</UNTRUSTED_DATA>";
    }

    private final AnalyticsEtlService etl;
    private final DeadLetterHandler deadLetter;
    private final DailyProgressReportRepository dprRepository;
    private final ActivityRepository activityRepository;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDprSubmitted(DprSubmittedEvent event) {
        try {
            // DELETED rows are gone from the OLTP store — analytics keeps the historical fact rows
            // intact (a future fact_dpr_logs reaper can sweep them); skip ETL for the delete event.
            if (event.eventType() == DprMutationType.DELETED) {
                log.debug("Skipping ETL for deleted DPR: project={} dpr={}", event.projectId(), event.dprId());
                return;
            }

            DailyProgressReport dpr = dprRepository.findById(event.dprId()).orElse(null);
            if (dpr == null) {
                log.warn("DPR not found for event: {}", event);
                return;
            }

            UUID activityId = resolveActivityId(event.projectId(), dpr.getActivityName());

            // Cumulative qty is no longer stored on the entity — recompute through the
            // event's reportDate so back-dated edits land with a correct cumulative.
            BigDecimal cumulative = dprRepository.sumQtyExecutedThroughDate(
                    event.projectId(), dpr.getActivityName(), dpr.getReportDate());
            Double cumulativeDouble = cumulative != null ? cumulative.doubleValue() : null;

            etl.insertDprLog(
                    event.projectId(), activityId, dpr.getId(), dpr.getReportDate(),
                    new UUID(0L, 0L),
                    dpr.getSupervisorName(),
                    dpr.getChainageFromM() != null ? dpr.getChainageFromM().doubleValue() : null,
                    dpr.getChainageToM() != null ? dpr.getChainageToM().doubleValue() : null,
                    event.qtyExecuted() != null ? event.qtyExecuted().doubleValue() : null,
                    cumulativeDouble,
                    dpr.getWeatherCondition(),
                    null,
                    sanitizeRemarks(dpr.getRemarks()));

            etl.insertActivityProgressDaily(
                    event.projectId(), activityId, dpr.getReportDate(),
                    null, null,
                    event.qtyExecuted() != null ? event.qtyExecuted().doubleValue() : null,
                    cumulativeDouble,
                    dpr.getChainageFromM() != null ? dpr.getChainageFromM().doubleValue() : null,
                    dpr.getChainageToM() != null ? dpr.getChainageToM().doubleValue() : null,
                    "dpr");

            log.debug("ETL processed DprSubmittedEvent: project={} dpr={} type={}",
                    event.projectId(), event.dprId(), event.eventType());
        } catch (Exception e) {
            log.error("ETL failed for DprSubmittedEvent: {}", event, e);
            meterRegistry.counter("bipros.analytics.etl.failures", "fact", "fact_dpr_logs").increment();
            deadLetter.record("project.daily_progress_reports", "fact_dpr_logs", event, e);
        }
    }

    private UUID resolveActivityId(UUID projectId, String activityName) {
        try {
            List<Activity> activities = activityRepository.findByProjectId(projectId);
            return activities.stream()
                    .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(activityName))
                    .findFirst()
                    .map(Activity::getId)
                    .orElse(new UUID(0L, 0L));
        } catch (Exception e) {
            return new UUID(0L, 0L);
        }
    }
}
