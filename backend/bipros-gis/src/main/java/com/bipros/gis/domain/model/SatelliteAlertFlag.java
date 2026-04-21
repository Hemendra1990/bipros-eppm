package com.bipros.gis.domain.model;

/**
 * Alert banding applied to a {@link ConstructionProgressSnapshot}, derived from
 * AI-computed variance and environmental indices (CVI/EDI/NDVI).
 *
 * <p>Matches the Excel M3 sheet's alert column:
 * <ul>
 *   <li>GREEN — on plan (|variance| &lt;= 5%)</li>
 *   <li>AMBER_VARIANCE_GT5 — soft warning (5 &lt; |variance| &lt;= 10)</li>
 *   <li>RED_VARIANCE_GT10 — hard warning (|variance| &gt; 10)</li>
 *   <li>RED_ENCROACHMENT — perimeter/encroachment detected</li>
 *   <li>AMBER_IDLE_ZONE — activity expected but CVI/EDI flat</li>
 * </ul>
 */
public enum SatelliteAlertFlag {
    GREEN,
    AMBER_VARIANCE_GT5,
    RED_VARIANCE_GT10,
    RED_ENCROACHMENT,
    AMBER_IDLE_ZONE
}
