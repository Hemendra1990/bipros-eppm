package com.bipros.permit.application.scheduling;

import com.bipros.integration.adapter.sms.SmsDispatcher;
import com.bipros.permit.domain.model.LifecycleEventType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitLifecycleEvent;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.repository.PermitLifecycleEventRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * For each {@code PENDING_*} permit stuck for more than the configured threshold,
 * raises an ESCALATED lifecycle event and dispatches an SMS to the next responsible role.
 * Runs every 30 minutes by default; configurable via {@code bipros.permit.escalation-fixed-delay-ms}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermitEscalationJob {

    private static final String JOB_NAME = "permit_escalation";

    private final PermitRepository permitRepository;
    private final PermitLifecycleEventRepository lifecycleRepository;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final SmsDispatcher smsDispatcher;

    @Scheduled(fixedDelayString = "${bipros.permit.escalation-fixed-delay-ms:1800000}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(5));
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            return;
        }

        Instant cutoff = now.minus(Duration.ofHours(24));
        List<PermitStatus> pending = List.of(
                PermitStatus.PENDING_SITE_ENGINEER,
                PermitStatus.PENDING_HSE,
                PermitStatus.AWAITING_GAS_TEST,
                PermitStatus.PENDING_PM);

        int raised = 0;
        for (PermitStatus status : pending) {
            for (Permit p : permitRepository.findStuck(status, cutoff)) {
                PermitLifecycleEvent ev = new PermitLifecycleEvent();
                ev.setPermitId(p.getId());
                ev.setEventType(LifecycleEventType.ESCALATED);
                ev.setOccurredAt(now);
                ev.setPayloadJson("{\"status\":\"" + status + "\"}");
                lifecycleRepository.save(ev);

                smsDispatcher.send(new SmsDispatcher.SmsMessage(
                        null,
                        "Permit %s has been awaiting %s for 24h+. Please action.".formatted(p.getPermitCode(), status),
                        "PERMIT_ESCALATION"));
                raised++;
            }
        }
        if (raised > 0) log.info("PermitEscalationJob raised {} escalations", raised);
    }
}
