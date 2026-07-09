package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.core.Severity;

import java.util.List;
import java.util.UUID;

/**
 * A single finding fully resolved for delivery to one recipient over one channel. Carries the
 * finding's narrative fields verbatim (money already arrives pre-formatted — never re-converted),
 * the recipient's contact points, and a relative frontend deep link (channels that need an
 * absolute URL prepend the configured app base URL).
 *
 * @param findingId          the source {@code AgentFinding} id (dedup + audit key)
 * @param projectId          owning project ({@code null} for cross-project findings)
 * @param findingType        stable machine type, e.g. {@code CRITICAL_PATH_SLIP}
 * @param severity           finding severity
 * @param title              short headline
 * @param whatHappened       the observed fact
 * @param whyItHappened      the driver / cause
 * @param businessImpact     the "so what"
 * @param recommendedAction  the next step
 * @param confidenceBasis    plain-English basis of the confidence statistic
 * @param stakeholderLabels  role-key labels the finding is addressed to (for the email footer)
 * @param recipientUserId    resolved recipient user id
 * @param recipientName      recipient display name ({@code null} if the user could not be loaded)
 * @param email              recipient email ({@code null}/blank when unknown)
 * @param phone              recipient phone/mobile ({@code null}/blank when unknown)
 * @param deepLink           relative frontend path, e.g. {@code /projects/{id}/schedule?focus=...}
 */
public record ResolvedNotification(
        UUID findingId,
        UUID projectId,
        String findingType,
        Severity severity,
        String title,
        String whatHappened,
        String whyItHappened,
        String businessImpact,
        String recommendedAction,
        String confidenceBasis,
        List<String> stakeholderLabels,
        UUID recipientUserId,
        String recipientName,
        String email,
        String phone,
        String deepLink) {
}
