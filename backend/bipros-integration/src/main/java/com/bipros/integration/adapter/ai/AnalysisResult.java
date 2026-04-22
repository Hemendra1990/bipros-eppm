package com.bipros.integration.adapter.ai;

/**
 * What the analyzer returns. All numeric fields may be {@code null} if the
 * model couldn't read the image or the run failed; {@code remarks} then
 * carries the reason so upstream code logs a useful message rather than
 * "progressPercent is null".
 *
 * @param progressPercent     0..100 estimate of construction completion
 * @param cvi                 Construction Visibility Index 0..100 — how much
 *                            built structure the model can see in the AOI
 * @param edi                 Earthwork Detection Index −1..+1 — change in
 *                            bare-earth vs. vegetation since baseline
 * @param ndviChange          change in NDVI from baseline, −1..+1
 * @param remarks             free-text rationale or error message
 * @param analyzerId          e.g. {@code "claude-vision:claude-sonnet-4-6"}
 * @param durationMs          wall-clock time spent in analyze()
 * @param costMicros          cost of the call in USD micro-cents
 *                            (Claude input+output tokens × rates × 1e6)
 */
public record AnalysisResult(
    Double progressPercent,
    Double cvi,
    Double edi,
    Double ndviChange,
    String remarks,
    String analyzerId,
    long durationMs,
    long costMicros
) {}
