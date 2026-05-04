package com.bipros.ai.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-model-family capabilities. Different OpenAI reasoning families accept
 * different {@code reasoning_effort} vocabularies — o1/o3 take
 * {@code minimal/low/medium/high}; gpt-5* take {@code none/low/medium/high/xhigh}.
 * Hard-coding any single value would break on the next model release; this
 * registry encodes the matrix once and downgrades unsupported values to the
 * closest valid choice instead of letting OpenAI 400 the request.
 *
 * <p>Detection is by case-insensitive prefix on the model name. Unknown models
 * fall through to {@link #DEFAULT}, which matches the broadest intersection
 * across all known reasoning families.
 */
@Component
@Slf4j
public class ModelCapabilityRegistry {

    /** Effort values accepted by the broadest set of OpenAI reasoning models. */
    public static final Set<String> UNIVERSAL_EFFORTS = Set.of("low", "medium", "high");

    public record Capabilities(
            Set<String> supportedReasoningEfforts,
            boolean reasoningModel,
            boolean supportsResponseFormat,
            boolean supportsNativeFiles
    ) {}

    private static final Capabilities DEFAULT =
            new Capabilities(UNIVERSAL_EFFORTS, false, true, true);

    /** prefix → capabilities; the longest matching prefix wins. Order matters: longest first. */
    private static final Map<String, Capabilities> RULES = new LinkedHashMap<>();
    static {
        // gpt-5 family (incl. gpt-5, gpt-5.5, gpt-5-mini, gpt-5-pro, gpt-5-yyyy-mm-dd snapshots)
        RULES.put("gpt-5", new Capabilities(
                Set.of("none", "low", "medium", "high", "xhigh"),
                /* reasoning */ true, true, true));
        // o1 family
        RULES.put("o1", new Capabilities(
                Set.of("minimal", "low", "medium", "high"),
                true, true, true));
        // o3 / o4 family
        RULES.put("o3", new Capabilities(
                Set.of("minimal", "low", "medium", "high"),
                true, true, true));
        RULES.put("o4", new Capabilities(
                Set.of("minimal", "low", "medium", "high"),
                true, true, true));
        // gpt-4 / gpt-4o / gpt-4.1 — non-reasoning; ignore reasoning_effort entirely.
        RULES.put("gpt-4", new Capabilities(Set.of(), false, true, true));
        // gpt-3.5 — legacy, kept for completeness
        RULES.put("gpt-3.5", new Capabilities(Set.of(), false, true, false));
    }

    public Capabilities forModel(String model) {
        if (model == null || model.isBlank()) return DEFAULT;
        String lower = model.toLowerCase(Locale.ROOT);
        // Longest-prefix match (so "gpt-5" wins over a bare default for "gpt-5.5-2026-04-23").
        String bestKey = null;
        for (String prefix : RULES.keySet()) {
            if (lower.startsWith(prefix) && (bestKey == null || prefix.length() > bestKey.length())) {
                bestKey = prefix;
            }
        }
        return bestKey == null ? DEFAULT : RULES.get(bestKey);
    }

    /**
     * Coerce a requested {@code reasoning_effort} into a value the model accepts.
     * Returns {@code null} when the model is non-reasoning (caller should skip
     * the parameter entirely). Returns the same value when supported. Otherwise
     * returns the closest valid value, with a WARN log so the operator can see
     * the downgrade.
     */
    public String resolveReasoningEffort(String model, String requested) {
        Capabilities caps = forModel(model);
        if (!caps.reasoningModel()) {
            return null;
        }
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String want = requested.toLowerCase(Locale.ROOT);
        if (caps.supportedReasoningEfforts().contains(want)) {
            return want;
        }
        // Map to the closest accepted value. The semantic ladder is:
        //   none < minimal < low < medium < high < xhigh
        List<String> ladder = List.of("none", "minimal", "low", "medium", "high", "xhigh");
        int wantIdx = ladder.indexOf(want);
        String fallback = caps.supportedReasoningEfforts().contains("low") ? "low"
                : caps.supportedReasoningEfforts().iterator().next();
        if (wantIdx >= 0) {
            // Walk outward from the requested rung. Prefer ABOVE (more reasoning) over
            // BELOW (less): if the user asked for "minimal" and the model only supports
            // {low, medium, high}, "low" is closer to the user's intent than "none" —
            // they wanted *some* reasoning, not zero.
            for (int dist = 1; dist < ladder.size(); dist++) {
                int above = wantIdx + dist, below = wantIdx - dist;
                if (above < ladder.size() && caps.supportedReasoningEfforts().contains(ladder.get(above))) {
                    fallback = ladder.get(above); break;
                }
                if (below >= 0 && caps.supportedReasoningEfforts().contains(ladder.get(below))) {
                    fallback = ladder.get(below); break;
                }
            }
        }
        log.warn("Model {} does not support reasoning_effort={}; downgrading to {}",
                model, requested, fallback);
        return fallback;
    }
}
