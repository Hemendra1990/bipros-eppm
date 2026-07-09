package com.bipros.integration.adapter.weather;

/**
 * A single geocoding hit from Open-Meteo's free geocoding API (no key). Carries just enough to
 * label the place in a picker and to anchor a weather lookup (latitude/longitude/timezone).
 *
 * @param name        city / place name (e.g. "Khasab")
 * @param admin1      first-level admin region / state (e.g. "Musandam"), nullable
 * @param admin2      second-level admin region, nullable
 * @param country     country display name (e.g. "Oman")
 * @param countryCode ISO-3166 alpha-2 code (e.g. "OM"), nullable
 * @param latitude    WGS84 latitude
 * @param longitude   WGS84 longitude
 * @param timezone    IANA timezone id (e.g. "Asia/Muscat"), nullable
 * @param population  population if known, nullable — used only to rank/label
 */
public record GeoResult(
        String name,
        String admin1,
        String admin2,
        String country,
        String countryCode,
        double latitude,
        double longitude,
        String timezone,
        Long population) {
}
