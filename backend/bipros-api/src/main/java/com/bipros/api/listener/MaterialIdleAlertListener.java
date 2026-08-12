package com.bipros.api.listener;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.api.notification.DprAlertConfig;
import com.bipros.api.notification.MaterialIdleAlertService;
import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprApprovalStatus;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

/**
 * Fires the idle-material check when an APPROVED DPR leaves its activity at or past the
 * configured % complete.
 *
 * <p><b>Why approval and not submission</b> (owner-approved deviation, 2026-08-12): approving is
 * the moment {@code ActivityProgressFromBoqListener} recomputes {@code percentComplete} and the
 * DPR's material lines start counting as consumption. Evaluated at submission the check would
 * read a stale percentage and ignore the very consumption the report is recording — under-firing
 * and over-stating the excess at the same time. The approver still sees the same numbers before
 * deciding, because the DPR panel computes them provisionally.
 *
 * <p>{@code @Order(50)} keeps this behind {@code ActivityProgressFromBoqListener} (@Order(10)),
 * which writes the percentage this listener reads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(50)
public class MaterialIdleAlertListener {

    private final DailyProgressReportRepository dprRepository;
    private final ActivityRepository activityRepository;
    private final MaterialIdleAlertService idleAlertService;
    private final DprAlertConfig alertConfig;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDpr(DprSubmittedEvent e) {
        if (e.eventType() == DprMutationType.DELETED || e.activityId() == null) return;
        if (!alertConfig.materialIdleEnabled()) return;

        DailyProgressReport dpr = dprRepository.findById(e.dprId()).orElse(null);
        if (dpr == null || dpr.getApprovalStatus() != DprApprovalStatus.APPROVED) return;

        Activity activity = activityRepository.findById(e.activityId()).orElse(null);
        if (activity == null || activity.getPercentComplete() == null) return;
        if (activity.getPercentComplete() < alertConfig.idleThresholds().percentTrigger()) return;

        try {
            idleAlertService.runForProject(dpr.getProjectId(), LocalDate.now());
        } catch (Exception ex) {
            log.warn("[MaterialIdleAlert] evaluation failed for dpr={}: {}", e.dprId(), ex.getMessage(), ex);
        }
    }
}
