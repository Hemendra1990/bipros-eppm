package com.bipros.integration.adapter.weather;

import java.util.List;

/**
 * A short weather forecast for a site: the resolved coordinates/timezone plus an ordered list of
 * daily entries (day 0 = today). Empty {@code days} means the provider was unavailable or returned
 * nothing — callers treat it as "no signal", never as "good weather".
 */
public record WeatherForecast(
        double latitude,
        double longitude,
        String timezone,
        List<DailyWx> days) {

    public static WeatherForecast empty(double latitude, double longitude) {
        return new WeatherForecast(latitude, longitude, null, List.of());
    }

    public boolean isEmpty() {
        return days == null || days.isEmpty();
    }
}
