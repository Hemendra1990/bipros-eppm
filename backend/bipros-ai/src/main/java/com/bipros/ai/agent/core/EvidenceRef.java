package com.bipros.ai.agent.core;

import java.util.UUID;

/**
 * A single piece of deterministic evidence backing a finding — a metric, an entity, a tool
 * result, or a chart. Rendered as evidence chips on the finding card and (for ENTITY refs)
 * deep-linked into the app via {@code linkUrl}. All values are produced by the agent's
 * {@code gather()} pass; the LLM narrator never fabricates evidence.
 *
 * @param type       one of METRIC | ENTITY | TOOL_RESULT | CHART
 * @param label      human label, e.g. "Critical path slip"
 * @param value      display value, e.g. "12 days" (already formatted, currency-neutral numbers stay raw)
 * @param entityType domain entity type for ENTITY refs (nullable), e.g. "activity"
 * @param entityId   domain entity id for ENTITY refs (nullable)
 * @param linkUrl    frontend deep link (nullable), e.g. "/projects/{id}/schedule?focus={activityId}"
 */
public record EvidenceRef(
        String type,
        String label,
        String value,
        String entityType,
        UUID entityId,
        String linkUrl) {

    public static EvidenceRef metric(String label, String value) {
        return new EvidenceRef("METRIC", label, value, null, null, null);
    }

    public static EvidenceRef entity(String label, String value, String entityType, UUID entityId, String linkUrl) {
        return new EvidenceRef("ENTITY", label, value, entityType, entityId, linkUrl);
    }
}
