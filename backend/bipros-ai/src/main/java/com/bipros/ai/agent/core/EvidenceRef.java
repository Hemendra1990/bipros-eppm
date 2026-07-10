package com.bipros.ai.agent.core;

import java.util.List;
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
 * @param series     a small time/category series for CHART refs (nullable); scalar refs leave it null
 */
public record EvidenceRef(
        String type,
        String label,
        String value,
        String entityType,
        UUID entityId,
        String linkUrl,
        Series series) {

    /**
     * A compact, chartable series carried by a CHART evidence ref — a handful of labelled points plus an
     * optional reference line (a threshold or target). Deterministic: the agent fills it from gathered data.
     *
     * @param kind     "COLUMN" (bars, e.g. rainfall/day) or "LINE" (trend, e.g. SPI over periods)
     * @param unit     display unit for values, e.g. "mm" ("" when unitless like an index)
     * @param points   the ordered points; ≤ ~12 keeps the card chart readable
     * @param refValue optional reference-line value (threshold/target), nullable
     * @param refLabel optional reference-line label, e.g. "20 mm" or "1.00 target", nullable
     */
    public record Series(
            String kind,
            String unit,
            List<Point> points,
            Double refValue,
            String refLabel) {

        /** One labelled point, e.g. ("Mon", 26.0) or ("09 Jul", 0.87). */
        public record Point(String label, double value) {
        }
    }

    public static EvidenceRef metric(String label, String value) {
        return new EvidenceRef("METRIC", label, value, null, null, null, null);
    }

    public static EvidenceRef entity(String label, String value, String entityType, UUID entityId, String linkUrl) {
        return new EvidenceRef("ENTITY", label, value, entityType, entityId, linkUrl, null);
    }

    public static EvidenceRef chart(String label, Series series) {
        return new EvidenceRef("CHART", label, null, null, null, null, series);
    }
}
