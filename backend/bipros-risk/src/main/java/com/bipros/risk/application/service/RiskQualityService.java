package com.bipros.risk.application.service;

import com.bipros.risk.application.dto.RiskAnalysisQuality;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates whether a {@link Risk} has been analysed properly. Four boolean criteria
 * are checked; each met criterion adds 1 to the score:
 *
 * <ul>
 *   <li>{@code hasOwner} — owner assigned</li>
 *   <li>{@code hasRating} — probability + at least one of cost/schedule impact rated</li>
 *   <li>{@code hasDescription} — description ≥ 50 characters</li>
 *   <li>{@code hasResponse} — at least one response with type + responsible party</li>
 * </ul>
 *
 * <p>4 → WELL_ANALYSED, 2-3 → PARTIALLY_ANALYSED, 0-1 → NOT_ANALYSED.
 *
 * <p>Pure read-side derivation — no persistence, recomputed on every read.
 */
@Service
public class RiskQualityService {

    /** Minimum description length for the {@code hasDescription} criterion. */
    public static final int DESCRIPTION_MIN_LENGTH = 50;

    public RiskAnalysisQuality assess(Risk risk, List<RiskResponse> responses) {
        Map<String, Boolean> criteria = new LinkedHashMap<>();
        criteria.put("hasOwner", risk.getOwnerId() != null);
        criteria.put("hasRating",
            risk.getProbability() != null
                && (risk.getImpactCost() != null || risk.getImpactSchedule() != null));
        criteria.put("hasDescription",
            risk.getDescription() != null
                && risk.getDescription().trim().length() >= DESCRIPTION_MIN_LENGTH);
        criteria.put("hasResponse", hasUsableResponse(responses));

        int score = 0;
        for (Boolean met : criteria.values()) {
            if (Boolean.TRUE.equals(met)) score++;
        }
        return new RiskAnalysisQuality(score, RiskAnalysisQuality.levelFor(score), criteria);
    }

    private static boolean hasUsableResponse(List<RiskResponse> responses) {
        if (responses == null || responses.isEmpty()) return false;
        for (RiskResponse r : responses) {
            if (r.getResponseType() != null && r.getResponsibleId() != null) return true;
        }
        return false;
    }
}
