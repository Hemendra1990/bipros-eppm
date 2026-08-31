package com.bipros.scheduling.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * A deterministic schedule what-if / change-impact request: a set of per-activity duration changes
 * to evaluate against the current network in memory (no persistence). Each change supplies EITHER an
 * absolute {@code newDurationDays} OR a signed {@code deltaDays} (added to the activity's current
 * remaining duration) — a positive delta models a delay, a negative delta models crashing.
 *
 * <p>Optional high-level {@code levers} model scenario forces (adding a resource/crew, a weather
 * delay, a procurement delay) that expand into per-activity duration overrides across many matching
 * activities. Levers are applied first; explicit {@code changes} take precedence and overwrite them.
 * Both {@code changes} and {@code levers} may be null for backward compatibility.
 */
public record WhatIfRequest(String scenarioLabel, List<ActivityChange> changes, List<ScenarioLever> levers) {

    public record ActivityChange(UUID activityId, Double newDurationDays, Double deltaDays) {}

    /**
     * A high-level scenario force that expands into per-activity duration overrides.
     * {@code magnitude} is interpreted per lever type (a % speed-up for ADD_RESOURCE, a day count
     * for the delay levers). {@code appliesToKeyword} narrows the affected activities by name;
     * when null/blank each lever falls back to a sensible default activity set.
     */
    public record ScenarioLever(LeverType type, Double magnitude, String appliesToKeyword) {}

    public enum LeverType { ADD_RESOURCE, WEATHER_DELAY, PROCUREMENT_DELAY }
}
