package com.bipros.integration.adapter.ai;

import java.time.LocalDate;

/**
 * Inputs the analyzer sees when estimating construction progress. Context is
 * provided to ground the model — knowing what the polygon is supposed to contain
 * and what the contractor claims cuts hallucination.
 *
 * @param rasterBytes       raw image (GeoTIFF or PNG) of the AOI
 * @param rasterMimeType    {@code image/tiff} or {@code image/png}
 * @param wbsPackageCode    e.g. {@code "DMIC-N03-P01-WP01"} — used in the prompt
 * @param wbsName           human-readable name — used in the prompt
 * @param claimedPercent    contractor-reported progress, nullable; helps the
 *                          model anchor its estimate but also lets us compute
 *                          variance later
 * @param captureDate       date the raster was captured
 */
public record AnalysisRequest(
    byte[] rasterBytes,
    String rasterMimeType,
    String wbsPackageCode,
    String wbsName,
    Double claimedPercent,
    LocalDate captureDate
) {}
