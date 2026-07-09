package com.bipros.integration.adapter.weather;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client over <a href="https://open-meteo.com">Open-Meteo</a>'s free, key-less APIs:
 * <ul>
 *   <li>{@link #geocode(String, int)} — place search via {@code geocoding-api.open-meteo.com}.</li>
 *   <li>{@link #forecast(double, double, int)} — daily forecast via {@code api.open-meteo.com}.</li>
 * </ul>
 *
 * <p>Both calls are best-effort: any transport/parse error is logged and degraded to an empty list /
 * {@link WeatherForecast#empty}, never rethrown — a weather outage must not break geocoding pickers
 * or the background agent pipeline. Timeouts are tight (mirrors {@code ClaudeVisionAnalyzer}) so a
 * hung upstream never ties up a request or scheduler thread.
 */
@Component
@Slf4j
public class OpenMeteoClient {

    private final RestClient geocodingHttp;
    private final RestClient forecastHttp;

    public OpenMeteoClient(
            @Value("${bipros.weather.geocoding-base-url:https://geocoding-api.open-meteo.com}") String geocodingBaseUrl,
            @Value("${bipros.weather.forecast-base-url:https://api.open-meteo.com}") String forecastBaseUrl,
            @Value("${bipros.weather.request-timeout-seconds:8}") int timeoutSeconds) {
        ClientHttpRequestFactory factory = timeoutFactory(Duration.ofSeconds(timeoutSeconds));
        this.geocodingHttp = RestClient.builder().baseUrl(geocodingBaseUrl).requestFactory(factory).build();
        this.forecastHttp = RestClient.builder().baseUrl(forecastBaseUrl).requestFactory(factory).build();
    }

    /**
     * Search places by name. Returns up to {@code count} matches ordered by the provider's own
     * relevance (population-weighted). Blank query or no matches → empty list.
     */
    public List<GeoResult> geocode(String query, int count) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(count, 20));
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/search")
                    .queryParam("name", query.strip())
                    .queryParam("count", limit)
                    .queryParam("language", "en")
                    .queryParam("format", "json")
                    .build()
                    .toUriString();
            JsonNode root = geocodingHttp.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !root.hasNonNull("results")) {
                return List.of();
            }
            List<GeoResult> out = new ArrayList<>();
            for (JsonNode r : root.get("results")) {
                out.add(new GeoResult(
                        r.path("name").asText(null),
                        r.path("admin1").asText(null),
                        r.path("admin2").asText(null),
                        r.path("country").asText(null),
                        r.path("country_code").asText(null),
                        r.path("latitude").asDouble(),
                        r.path("longitude").asDouble(),
                        r.path("timezone").asText(null),
                        r.hasNonNull("population") ? r.get("population").asLong() : null));
            }
            return out;
        } catch (Exception e) {
            log.warn("[OpenMeteo] geocode '{}' failed: {}", query, e.toString());
            return List.of();
        }
    }

    /**
     * Daily forecast for a site. Day 0 is today (site timezone). Returns {@link WeatherForecast#empty}
     * on any failure. {@code days} is clamped to 1..16 (Open-Meteo's free daily horizon).
     */
    public WeatherForecast forecast(double latitude, double longitude, int days) {
        int horizon = Math.max(1, Math.min(days, 16));
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/forecast")
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                            + "windspeed_10m_max,weathercode")
                    .queryParam("timezone", "auto")
                    .queryParam("forecast_days", horizon)
                    .build()
                    .toUriString();
            JsonNode root = forecastHttp.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !root.has("daily")) {
                return WeatherForecast.empty(latitude, longitude);
            }
            JsonNode daily = root.get("daily");
            JsonNode time = daily.path("time");
            List<DailyWx> out = new ArrayList<>();
            for (int i = 0; i < time.size(); i++) {
                LocalDate date = LocalDate.parse(time.get(i).asText());
                Integer code = intAt(daily, "weathercode", i);
                out.add(new DailyWx(
                        date,
                        doubleAt(daily, "temperature_2m_max", i),
                        doubleAt(daily, "temperature_2m_min", i),
                        doubleAt(daily, "precipitation_sum", i),
                        doubleAt(daily, "windspeed_10m_max", i),
                        code,
                        describeCode(code)));
            }
            return new WeatherForecast(latitude, longitude, root.path("timezone").asText(null), out);
        } catch (Exception e) {
            log.warn("[OpenMeteo] forecast ({},{}) failed: {}", latitude, longitude, e.toString());
            return WeatherForecast.empty(latitude, longitude);
        }
    }

    private static Double doubleAt(JsonNode daily, String field, int i) {
        JsonNode arr = daily.path(field);
        if (!arr.isArray() || i >= arr.size() || arr.get(i).isNull()) return null;
        return arr.get(i).asDouble();
    }

    private static Integer intAt(JsonNode daily, String field, int i) {
        JsonNode arr = daily.path(field);
        if (!arr.isArray() || i >= arr.size() || arr.get(i).isNull()) return null;
        return arr.get(i).asInt();
    }

    /** WMO weather-interpretation code → short human label (subset covering the common bands). */
    private static String describeCode(Integer code) {
        if (code == null) return null;
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2 -> "Mainly clear";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63 -> "Rain";
            case 65 -> "Heavy rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81 -> "Rain showers";
            case 82 -> "Violent rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }

    private static ClientHttpRequestFactory timeoutFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }
}
