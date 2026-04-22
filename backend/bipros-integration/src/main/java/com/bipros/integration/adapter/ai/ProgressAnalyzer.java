package com.bipros.integration.adapter.ai;

/**
 * Vendor-neutral AI/vision analyzer for construction-progress estimation.
 * Implementations include {@link ClaudeVisionAnalyzer} (default) and, later,
 * an indices-only math analyzer or a self-hosted SAM analyzer. Registry lookup
 * via {@link ProgressAnalyzerRegistry}.
 */
public interface ProgressAnalyzer {

    /** Registry key. e.g. "claude-vision", "indices-only". */
    String providerId();

    /**
     * Run the analyzer. Must not throw for model-level failures; return an
     * {@link AnalysisResult} with null percent + non-null remarks so the
     * caller can still persist a snapshot row for audit.
     */
    AnalysisResult analyze(AnalysisRequest request);
}
