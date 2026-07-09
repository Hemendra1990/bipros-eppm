package com.bipros.integration.adapter.weather;

import java.time.LocalDate;

/**
 * One day of weather (a forecast day, or — for day 0 — the current-day actuals). All values are
 * nullable because the provider can omit any field; downstream rules must null-check.
 *
 * @param date        the calendar day (in the site's timezone)
 * @param tempMaxC    max temperature °C
 * @param tempMinC    min temperature °C
 * @param rainfallMm  total precipitation mm
 * @param windMaxKmh  max wind speed km/h
 * @param weatherCode WMO weather-interpretation code
 * @param condition   human-readable condition derived from {@code weatherCode} (e.g. "Heavy rain")
 */
public record DailyWx(
        LocalDate date,
        Double tempMaxC,
        Double tempMinC,
        Double rainfallMm,
        Double windMaxKmh,
        Integer weatherCode,
        String condition) {
}
