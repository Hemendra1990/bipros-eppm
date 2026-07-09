package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.Severity;
import com.bipros.ai.agent.domain.AgentFinding;
import com.bipros.ai.agent.domain.AgentNotificationDelivery;
import com.bipros.ai.agent.domain.AgentNotificationDeliveryRepository;
import com.bipros.ai.agent.domain.AgentNotificationRule;
import com.bipros.ai.agent.domain.AgentNotificationRuleRepository;
import com.bipros.ai.agent.domain.DeliveryStatus;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes a notifiable finding to channels + recipients.
 *
 * <p>For a finding it (1) resolves the routing rule — per-project {@code AgentNotificationRule},
 * else the global rule, else the yml default in {@link AgentNotifyProperties} — (2) resolves
 * recipients via {@link StakeholderResolver}, and (3) for each (channel, recipient) that passes
 * dedup, calls {@code channel.send} and records an {@link AgentNotificationDelivery} audit row.
 * Non-immediate rules are left for the daily digest.
 *
 * <p>Dedup suppresses a (finding, channel, recipient) that already has a delivery row, or an in-app
 * notification for the same finding created within the last 24 hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRouter {

    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final AgentNotificationRuleRepository ruleRepository;
    private final AgentNotificationDeliveryRepository deliveryRepository;
    private final AgentNotifyProperties properties;
    private final StakeholderResolver stakeholderResolver;
    private final AgentMemoryService memoryService;
    private final NotificationService notificationService;
    private final List<NotificationChannel> channels;

    /** Route one finding through its resolved rule. Never throws — per-channel failures are isolated. */
    public void route(AgentFinding finding) {
        if (finding == null || finding.getId() == null) {
            return;
        }
        RoutingRule rule = resolveRule(finding.getProjectId(), finding.getSeverity());
        if (rule == null || rule.channels().isEmpty()) {
            return;
        }
        if (!rule.immediate()) {
            // Deferred severities are batched by AgentDigestJob.
            return;
        }

        List<StakeholderResolver.Recipient> recipients = stakeholderResolver.resolve(finding);
        if (recipients.isEmpty()) {
            log.debug("No recipients resolved for finding {} (project {})", finding.getId(), finding.getProjectId());
            return;
        }

        Map<String, NotificationChannel> byKey = channelsByKey();
        String deepLink = deepLinkFor(finding);

        for (String channelKey : rule.channels()) {
            NotificationChannel channel = byKey.get(channelKey);
            if (channel == null) {
                log.debug("Routing rule references unknown channel '{}' — skipping", channelKey);
                continue;
            }
            if (!channel.isEnabled()) {
                continue;
            }
            for (StakeholderResolver.Recipient r : recipients) {
                if (r.userId() == null) {
                    continue;
                }
                if (isDuplicate(finding.getId(), channelKey, r.userId())) {
                    continue;
                }
                ResolvedNotification rn = toResolved(finding, r, deepLink);
                dispatch(channel, rn, finding.getId(), channelKey);
            }
        }
    }

    private void dispatch(NotificationChannel channel, ResolvedNotification rn, UUID findingId, String channelKey) {
        DeliveryStatus status = DeliveryStatus.SENT;
        String detail = null;
        try {
            channel.send(rn);
        } catch (Exception ex) {
            // Channels are contracted not to throw; this is a defensive guard so the audit stays honest.
            status = DeliveryStatus.FAILED;
            detail = truncate(ex.getMessage());
            log.warn("Channel '{}' threw for finding {}: {}", channelKey, findingId, ex.getMessage());
        }
        record(findingId, channelKey, rn.recipientUserId(), status, detail);
    }

    private boolean isDuplicate(UUID findingId, String channelKey, UUID userId) {
        if (deliveryRepository.existsByFindingIdAndChannelKeyAndRecipientUserId(findingId, channelKey, userId)) {
            return true;
        }
        Instant since = Instant.now().minus(DEDUP_WINDOW);
        return notificationService.existsSince(findingId, InAppChannel.NOTIFICATION_TYPE, userId, since);
    }

    private void record(UUID findingId, String channelKey, UUID userId, DeliveryStatus status, String detail) {
        try {
            AgentNotificationDelivery d = new AgentNotificationDelivery();
            d.setFindingId(findingId);
            d.setChannelKey(channelKey);
            d.setRecipientUserId(userId);
            d.setStatus(status);
            d.setDetail(detail);
            if (status == DeliveryStatus.SENT) {
                d.setSentAt(Instant.now());
            }
            deliveryRepository.save(d);
        } catch (Exception ex) {
            log.warn("Failed to record delivery for finding {} channel {}: {}", findingId, channelKey, ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- rule + recipient helpers

    private RoutingRule resolveRule(UUID projectId, Severity severity) {
        if (severity == null) {
            return null;
        }
        if (projectId != null) {
            Optional<AgentNotificationRule> perProject =
                    ruleRepository.findByProjectIdAndSeverity(projectId, severity);
            if (perProject.isPresent()) {
                return toRule(perProject.get());
            }
        }
        Optional<AgentNotificationRule> global = ruleRepository.findByProjectIdIsNullAndSeverity(severity);
        if (global.isPresent()) {
            return toRule(global.get());
        }
        AgentNotifyProperties.Routing yml = properties.getRouting().get(severity.name().toLowerCase(Locale.ROOT));
        if (yml == null) {
            return null;
        }
        return new RoutingRule(parseChannels(yml.getChannels()), yml.isImmediate());
    }

    private static RoutingRule toRule(AgentNotificationRule r) {
        return new RoutingRule(parseChannels(r.getChannelsCsv()), r.isImmediate());
    }

    private static List<String> parseChannels(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** The finding's primary deep link: first evidence link, else the project page. Reused by the digest. */
    public String deepLinkFor(AgentFinding finding) {
        for (EvidenceRef e : memoryService.readEvidence(finding)) {
            if (e.linkUrl() != null && !e.linkUrl().isBlank()) {
                return e.linkUrl();
            }
        }
        return finding.getProjectId() != null ? "/projects/" + finding.getProjectId() : "/";
    }

    /** Build the per-recipient delivery payload for a finding. Reused by the digest job. */
    public ResolvedNotification toResolved(AgentFinding f, StakeholderResolver.Recipient r, String deepLink) {
        List<String> labels = new ArrayList<>(memoryService.readStakeholders(f).keySet());
        return new ResolvedNotification(
                f.getId(),
                f.getProjectId(),
                f.getFindingType(),
                f.getSeverity(),
                f.getTitle(),
                f.getWhatHappened(),
                f.getWhyItHappened(),
                f.getBusinessImpact(),
                f.getRecommendedAction(),
                f.getConfidenceBasis(),
                labels,
                r.userId(),
                r.name(),
                r.email(),
                r.phone(),
                deepLink);
    }

    private Map<String, NotificationChannel> channelsByKey() {
        return channels.stream().collect(Collectors.toMap(
                NotificationChannel::key, Function.identity(), (a, b) -> a));
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    /** Effective routing rule for a severity: which channels, and whether to send immediately. */
    public record RoutingRule(List<String> channels, boolean immediate) {
    }
}
