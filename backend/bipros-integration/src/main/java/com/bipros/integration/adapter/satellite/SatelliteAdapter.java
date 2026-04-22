package com.bipros.integration.adapter.satellite;

import org.locationtech.jts.geom.Polygon;

import java.time.LocalDate;
import java.util.List;

/**
 * Vendor-neutral satellite imagery adapter. One implementation per provider:
 * {@code SentinelHubAdapter} is the default; future adapters for Planet Labs,
 * Maxar, or ISRO Bhuvan plug in behind the same interface and the
 * {@link SatelliteAdapterRegistry} resolves them by {@link #vendorId()}.
 */
public interface SatelliteAdapter {

    /** Unique, stable identifier used as a registry key. e.g. "sentinel-hub". */
    String vendorId();

    /**
     * Find scenes that intersect the AOI within the date window. Must be cheap
     * (catalog query, no raster download). May return an empty list if the
     * vendor has no coverage or the API is disabled.
     */
    List<SceneDescriptor> findImagery(Polygon aoi, LocalDate from, LocalDate to);

    /**
     * Fetch the actual raster bytes for one scene, clipped to the AOI.
     *
     * @param sceneId    identifier from {@link SceneDescriptor#sceneId()}
     * @param aoi        polygon to clip to — minimises raster size
     * @param maxWidthPx approximate output pixel width; adapter picks the
     *                   closest native resolution ≤ this value
     * @return GeoTIFF or PNG bytes (check content type per adapter)
     */
    byte[] fetchRaster(String sceneId, Polygon aoi, int maxWidthPx);

    /** Content type returned by {@link #fetchRaster}. Usually image/tiff. */
    default String rasterContentType() { return "image/tiff"; }
}
