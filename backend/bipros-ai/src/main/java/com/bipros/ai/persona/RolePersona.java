package com.bipros.ai.persona;

import java.util.List;

/**
 * Per-role persona block appended to the system prompt. Anchors tone and
 * KPI focus for that profile so the LLM defaults to the questions the
 * profile cares about.
 *
 * @param headline      one-line "You are assisting a Site Manager."
 * @param primaryKpis   3-5 KPI names in business terms ("Labour utilization %", …)
 * @param preferTools   tool names this profile should reach for first
 * @param framingHint   one short sentence on how to frame answers
 */
public record RolePersona(
        String headline,
        List<String> primaryKpis,
        List<String> preferTools,
        String framingHint
) {
    /** Renders the persona as a prompt block. Returns "" for the null persona. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n────────────────────────────────────────\n");
        sb.append("ROLE PERSONA\n");
        sb.append("────────────────────────────────────────\n");
        sb.append(headline).append("\n");
        sb.append("Primary KPIs to lead with: ")
                .append(String.join(", ", primaryKpis)).append(".\n");
        sb.append("When the user's question is open-ended, prefer these tools first: ")
                .append(String.join(", ", preferTools)).append(".\n");
        sb.append(framingHint).append("\n");
        sb.append(constructionDomainSuffix());
        return sb.toString();
    }

    /**
     * Construction-domain rules that anchor the AI on real EPC site practice — SC-180
     * Khasab–Daba is the customer's flagship project. Included on every persona render so
     * the orchestrator can rely on the persona block for these guarantees regardless of
     * which role-specific persona was chosen. Static so the orchestrator can also splice
     * it in unconditionally when no role persona is matched.
     */
    public static String constructionDomainSuffix() {
        return """

            ────────────────────────────────────────
            CONSTRUCTION-DOMAIN RULES (ALWAYS APPLY)
            ────────────────────────────────────────
            You are advising on EPC construction project execution. When the user asks about supervisor
            performance, cost, variance, productivity, or any formula-related question, you MUST:
              1. Call `formula_validate` (or `supervisor` for supervisor-specific rollups)
                 before answering. Do not estimate or guess from training data.
              2. Show the formula in human-readable form (e.g., "CV = EV − AC"), list the numeric
                 inputs you used (with units), and report the computed value.
              3. Cite the data scope: number of DPR rows aggregated, date range, and which entity
                 (EvmCalculation / DprManpower / DprEquipment / ConcretePour) supplied each input.
              4. For Daily Balance Sheet questions, call `dbs_report`. For concrete production
                 questions, query ConcretePour aggregates.
              5. Convert chainage values to "km+m" format when presenting to users (45000 → "45+000").
              6. Default currency is OMR (Omani Rial); state it explicitly.
            """;
    }
}
