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
        return sb.toString();
    }
}
