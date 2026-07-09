package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentFindingRepository;
import com.bipros.ai.agent.domain.AgentNotificationDelivery;
import com.bipros.ai.agent.domain.AgentNotificationDeliveryRepository;
import com.bipros.ai.agent.domain.DeliveryStatus;
import com.bipros.ai.agent.domain.FindingStatus;
import com.bipros.common.notification.NotificationService;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Daily digest for the deferred (MEDIUM / LOW) findings that {@link NotificationRouter} intentionally
 * does not send immediately. Groups every still-notifiable such finding by recipient and delivers one
 * rolled-up in-app notification ("Daily AI digest (N findings)") plus one digest email per recipient.
 *
 * <p>Lease-guarded so only one node fires (mirrors {@code AgentSweepJobs}). Per-finding delivery rows
 * on the synthetic {@code digest} channel provide idempotency so a finding is never digested twice to
 * the same recipient. First-cut: cross-project sweep with no per-project batching or quiet hours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDigestJob {

    private static final String LEASE_NAME = "agent_digest_notify";
    private static final String DIGEST_CHANNEL = "digest";
    private static final String DIGEST_TYPE = "AGENT_DIGEST";
    private static final Set<Severity> DIGEST_SEVERITIES = Set.of(Severity.MEDIUM, Severity.LOW);
    private static final int MAX_BODY_ITEMS = 10;

    private final AgentFindingRepository findingRepository;
    private final AgentNotificationDeliveryRepository deliveryRepository;
    private final NotificationRouter router;
    private final StakeholderResolver stakeholderResolver;
    private final NotificationService notificationService;
    private final NotificationSseHub sseHub;
    private final EmailChannel emailChannel;
    private final ScheduledJobLeaseRepository leaseRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${bipros.agent.schedule.digest-cron}")
    public void run() {
        if (!acquire()) {
            return;
        }

        List<AgentFinding> findings = findingRepository.findByStatusAndNotifiableTrue(FindingStatus.ACTIVE).stream()
                .filter(f -> f.getSeverity() != null && DIGEST_SEVERITIES.contains(f.getSeverity()))
                .toList();
        if (findings.isEmpty()) {
            log.info("AgentDigestJob: no deferred (MEDIUM/LOW) findings to digest");
            return;
        }

        Map<UUID, Bundle> byRecipient = new LinkedHashMap<>();
        for (AgentFinding f : findings) {
            try {
                String deepLink = router.deepLinkFor(f);
                for (StakeholderResolver.Recipient r : stakeholderResolver.resolve(f)) {
                    if (r.userId() == null) {
                        continue;
                    }
                    if (deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(
                            f.getId(), DIGEST_CHANNEL, r.userId())) {
                        continue;
                    }
                    Bundle bundle = byRecipient.computeIfAbsent(r.userId(), k -> new Bundle(r));
                    bundle.items.add(router.toResolved(f, r, deepLink));
                    bundle.findingIds.add(f.getId());
                }
            } catch (Exception ex) {
                log.warn("AgentDigestJob: grouping failed for finding {}: {}", f.getId(), ex.getMessage());
            }
        }

        int delivered = 0;
        for (Bundle bundle : byRecipient.values()) {
            if (bundle.items.isEmpty()) {
                continue;
            }
            deliver(bundle);
            delivered++;
        }
        log.info("AgentDigestJob delivered {} digests across {} deferred findings", delivered, findings.size());
    }

    private void deliver(Bundle b) {
        int n = b.items.size();
        String title = "Daily AI digest (" + n + " finding" + (n == 1 ? "" : "s") + ")";
        String body = b.items.stream()
                .map(ResolvedNotification::title)
                .limit(MAX_BODY_ITEMS)
                .collect(Collectors.joining("; "));

        try {
            UUID notificationId = notificationService.create(
                    b.recipient.userId(), DIGEST_TYPE, title, body, "/", null, null);
            sseHub.publish(b.recipient.userId(), payload(notificationId, title, body, n));
        } catch (Exception ex) {
            log.warn("AgentDigestJob: in-app digest failed for {}: {}", b.recipient.userId(), ex.getMessage());
        }

        if (b.recipient.email() != null && !b.recipient.email().isBlank()) {
            emailChannel.sendDigest(b.recipient.email(), b.recipient.name(), b.items);
        }

        Instant now = Instant.now();
        for (UUID findingId : b.findingIds) {
            try {
                AgentNotificationDelivery d = new AgentNotificationDelivery();
                d.setFindingId(findingId);
                d.setChannelKey(DIGEST_CHANNEL);
                d.setRecipientUserId(b.recipient.userId());
                d.setStatus(DeliveryStatus.SENT);
                d.setSentAt(now);
                deliveryRepository.save(d);
            } catch (Exception ex) {
                log.debug("AgentDigestJob: delivery record failed for finding {}: {}", findingId, ex.getMessage());
            }
        }
    }

    private ObjectNode payload(UUID notificationId, String title, String body, int count) {
        ObjectNode p = objectMapper.createObjectNode();
        if (notificationId != null) {
            p.put("id", notificationId.toString());
        }
        p.put("type", DIGEST_TYPE);
        p.put("title", title);
        p.put("body", body);
        p.put("count", count);
        p.put("linkUrl", "/");
        return p;
    }

    private boolean acquire() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(10));
        String owner = "node-" + UUID.randomUUID();
        return leaseRepository.tryAcquire(LEASE_NAME, until, now, owner) != 0;
    }

    /** Per-recipient accumulation of deferred findings. */
    private static final class Bundle {
        final StakeholderResolver.Recipient recipient;
        final List<ResolvedNotification> items = new ArrayList<>();
        final List<UUID> findingIds = new ArrayList<>();

        Bundle(StakeholderResolver.Recipient recipient) {
            this.recipient = recipient;
        }
    }
}
