package com.bipros.risk.application.dto;

import java.util.Map;

/**
 * Computed assessment of how completely a Risk has been analysed. Drives the
 * "Analysis" badge on the risk register so PMs can spot skeleton entries that look
 * populated but aren't ready for review.
 *
 * <p>Pure derivation — no schema field. Recomputed on each read.
 */
public record RiskAnalysisQuality(
    int score,
    QualityLevel level,
    Map<String, Boolean> criteria) {

    public enum QualityLevel {
        /** 0–1 of the 4 criteria met. */
        NOT_ANALYSED,
        /** 2–3 of the 4 criteria met. */
        PARTIALLY_ANALYSED,
        /** All 4 criteria met. */
        WELL_ANALYSED
    }

    public static QualityLevel levelFor(int score) {
        if (score >= 4) return QualityLevel.WELL_ANALYSED;
        if (score >= 2) return QualityLevel.PARTIALLY_ANALYSED;
        return QualityLevel.NOT_ANALYSED;
    }
}
