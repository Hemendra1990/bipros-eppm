package com.bipros.risk.domain.model;

/**
 * P6 / PMBOK risk response strategies. Threat strategies and opportunity strategies are
 * mirrored: AVOID↔EXPLOIT, MITIGATE↔ENHANCE, TRANSFER↔SHARE, ACCEPT↔ACCEPT.
 */
public enum RiskResponseType {
    // Threat strategies
    AVOID,
    MITIGATE,
    TRANSFER,
    ACCEPT,
    // Opportunity strategies
    EXPLOIT,
    ENHANCE,
    SHARE;

    /**
     * @return true when this strategy is valid for the given risk type. ACCEPT is valid for both.
     */
    public boolean isValidFor(RiskType riskType) {
        if (this == ACCEPT) return true;
        return switch (this) {
            case AVOID, MITIGATE, TRANSFER -> riskType == RiskType.THREAT;
            case EXPLOIT, ENHANCE, SHARE -> riskType == RiskType.OPPORTUNITY;
            case ACCEPT -> true;
        };
    }
}
