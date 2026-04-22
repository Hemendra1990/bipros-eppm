package com.bipros.integration.adapter.satellite;

import org.locationtech.jts.geom.Polygon;

import java.time.LocalDate;

/**
 * Metadata describing one satellite scene the adapter has located. Does NOT
 * include the raster bytes — those are fetched lazily via
 * {@link SatelliteAdapter#fetchRaster}. Keeps {@code findImagery} cheap.
 *
 * @param sceneId           vendor-unique scene identifier (e.g. Sentinel Hub's
 *                          tile-path-like string). MUST be stable so the
 *                          ingestion dedupe via satellite_images.scene_id works
 * @param vendorId          matches the adapter's {@link SatelliteAdapter#vendorId()}
 * @param captureDate       UTC date the satellite image was captured
 * @param cloudCoverPercent 0-100, nullable if the vendor doesn't report it
 * @param bbox              scene footprint as a JTS Polygon in EPSG:4326
 * @param previewUrl        optional small thumbnail URL for the UI gallery
 */
public record SceneDescriptor(
    String sceneId,
    String vendorId,
    LocalDate captureDate,
    Double cloudCoverPercent,
    Polygon bbox,
    String previewUrl
) {}
