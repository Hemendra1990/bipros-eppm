package com.bipros.permit.application.scheduling;

import com.bipros.permit.domain.model.LifecycleEventType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitLifecycleEvent;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.repository.PermitLifecycleEventRepository;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.ScheduledJobLeaseRepository;
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
 * Sweeps issued or in-progress permits whose validity window has lapsed and marks them EXPIRED.
 * Runs at 02:05 UTC daily; configurable via {@code bipros.permit.expiry-cron}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermitExpiryJob {

    private static final String JOB_NAME = "permit_expiry";

    private final PermitRepository permitRepository;
    private final PermitLifecycleEventRepository lifecycleRepository;
    private final ScheduledJobLeaseRepository leaseRepository;

    @Scheduled(cron = "${bipros.permit.expiry-cron:0 5 2 * * *}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(5));
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            log.debug("PermitExpiryJob skipped — another node holds the lease");
            return;
        }

        List<Permit> expired = permitRepository.findExpired(
                List.of(PermitStatus.ISSUED, PermitStatus.IN_PROGRESS), now);
        for (Permit p : expired) {
            p.setStatus(PermitStatus.EXPIRED);
            p.setExpiredAt(now);
            PermitLifecycleEvent ev = new PermitLifecycleEvent();
            ev.setPermitId(p.getId());
            ev.setEventType(LifecycleEventType.EXPIRED);
            ev.setOccurredAt(now);
            lifecycleRepository.save(ev);
        }
        if (!expired.isEmpty()) log.info("PermitExpiryJob expired {} permits", expired.size());
    }
}
