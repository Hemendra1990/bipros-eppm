package com.bipros.integration.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.integration.adapter.weather.GeoResult;
import com.bipros.integration.adapter.weather.OpenMeteoClient;
import com.bipros.integration.adapter.weather.WeatherForecast;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Server-side proxy for Open-Meteo geocoding. The browser cannot call Open-Meteo directly — the
 * frontend CSP restricts {@code connect-src} to self + the backend — so the location picker hits
 * this endpoint instead, and the backend performs the outbound call.
 *
 * <p>Read-only place search; any authenticated user may query it (an admin uses it while setting a
 * project's site location). If {@code country} (ISO-3166 alpha-2) is supplied, results are filtered
 * to that country server-side, since Open-Meteo's search has no country parameter of its own.
 */
@RestController
@RequestMapping("/v1/geo")
@RequiredArgsConstructor
public class GeoController {

    private final OpenMeteoClient openMeteoClient;

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<GeoResult>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "count", required = false, defaultValue = "10") int count) {
        List<GeoResult> results = openMeteoClient.geocode(query, count);
        if (country != null && !country.isBlank()) {
            String cc = country.strip().toUpperCase(Locale.ROOT);
            results = results.stream()
                    .filter(r -> cc.equalsIgnoreCase(r.countryCode()))
                    .toList();
        }
        return ApiResponse.ok(results);
    }

    /**
     * Live daily forecast for a coordinate — the browser-facing proxy for the site weather panel.
     * {@code days} defaults to 7 (clamped 1..16 by the client). Returns an empty forecast on any
     * upstream failure.
     */
    @GetMapping("/forecast")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<WeatherForecast> forecast(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "days", required = false, defaultValue = "7") int days) {
        return ApiResponse.ok(openMeteoClient.forecast(lat, lon, days));
    }
}
