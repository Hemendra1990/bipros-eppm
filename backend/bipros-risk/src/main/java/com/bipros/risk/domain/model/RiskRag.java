package com.bipros.risk.domain.model;

/**
 * IC-PMS M7 risk RAG band. CRIMSON = severe/catastrophic escalation;
 * OPPORTUNITY = upside risk tracked alongside threats.
 */
public enum RiskRag {
    CRIMSON,
    RED,
    AMBER,
    GREEN,
    OPPORTUNITY
}
