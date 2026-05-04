package com.bipros.project.application.listener;

import com.bipros.common.event.VariationOrderApprovedEvent;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Reacts to {@link VariationOrderApprovedEvent} by:
 * <ol>
 *   <li>Logging a typed audit-log entry summarising the impact, so the project's change
 *       history clearly shows "VO-001 approved: +5 days, +₹200000" without the user having
 *       to cross-reference contract and project audit streams.</li>
 *   <li>Setting {@code requires_rebaseline = true} on the project so the Baselines tab
 *       can surface a "needs re-baseline" banner. The flag is cleared the next time a
 *       baseline is taken (see BaselineService.createBaseline).</li>
 * </ol>
 *
 * <p>Impact fields are advisory only — this listener never edits activities or budgets.
 * The planner decides how to amend the schedule (extend an activity? add new ones?
 * compress others?) and re-baselines once the plan reflects the change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VariationOrderApprovedListener {

    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVariationOrderApproved(VariationOrderApprovedEvent event) {
        Project project = projectRepository.findById(event.projectId()).orElse(null);
        if (project == null) {
            log.warn("VO {} approved for missing project {} — skipping change-log entry",
                event.voId(), event.projectId());
            return;
        }

        String summary = buildSummary(event);
        auditService.logUpdate(
            "Project", event.projectId(), "variationOrderApproved", null, summary);

        if (!project.isRequiresRebaseline()) {
            project.setRequiresRebaseline(true);
            projectRepository.save(project);
            log.info("Project {} flagged requiresRebaseline=true after VO {} approved",
                event.projectId(), event.voNumber());
        }
    }

    private String buildSummary(VariationOrderApprovedEvent event) {
        StringBuilder sb = new StringBuilder("VO ").append(event.voNumber()).append(" approved");
        boolean hasDetail = false;
        if (event.impactOnScheduleDays() != null && event.impactOnScheduleDays() != 0) {
            sb.append(": ").append(formatDays(event.impactOnScheduleDays()));
            hasDetail = true;
        }
        if (event.impactOnBudget() != null && event.impactOnBudget().signum() != 0) {
            sb.append(hasDetail ? ", " : ": ").append(formatMoney(event.impactOnBudget()));
        }
        return sb.toString();
    }

    private String formatDays(int days) {
        return (days > 0 ? "+" : "") + days + " day" + (Math.abs(days) == 1 ? "" : "s");
    }

    private String formatMoney(BigDecimal amount) {
        return (amount.signum() > 0 ? "+" : "") + amount.toPlainString();
    }
}
