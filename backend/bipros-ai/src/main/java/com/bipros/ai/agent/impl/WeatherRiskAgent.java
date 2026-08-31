package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.integration.adapter.weather.DailyWx;
import com.bipros.integration.adapter.weather.OpenMeteoClient;
import com.bipros.integration.adapter.weather.WeatherForecast;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Weather-risk agent. Deterministic {@link #gather} over a live Open-Meteo forecast for the
 * project's configured site coordinates, surfacing construction-relevant weather threats the site
 * team should plan around:
 *
 * <ul>
 *   <li>{@code WEATHER_RAIN_RISK} — forecast days with heavy precipitation (earthworks, concrete
 *       pours, excavation flooding, access).</li>
 *   <li>{@code WEATHER_WIND_RISK} — forecast days with high wind (crane / lifting / formwork / HSE).</li>
 *   <li>{@code WEATHER_HEAT_RISK} — forecast days of extreme heat (labour productivity + heat-stress).</li>
 *   <li>{@code WEATHER_COLD_RISK} — forecast days near/below freezing (concrete curing).</li>
 * </ul>
 *
 * <p>Only runs for projects with a resolved site latitude/longitude AND
 * {@code weatherMonitoringEnabled}. Absent coordinates, disabled monitoring, or an unavailable
 * provider all degrade to an empty result — the pipeline never blocks on weather. Thresholds are
 * CONFIGURABLE constants below (no SLA/parameter entity exists). Confidence is deterministic and
 * decays with how far into the forecast the first adverse day sits (a nearer forecast is surer).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherRiskAgent extends AbstractAgent {

    private static final String KEY = "weather_risk";
    private static final Duration TTL = Duration.ofDays(2);
    private static final int FORECAST_DAYS = 7;

    // ── Construction weather thresholds (per forecast day) ──
    private static final double RAIN_MM_HIGH = 20.0;
    private static final double RAIN_MM_SEVERE = 50.0;
    private static final double WIND_KMH_HIGH = 40.0;
    private static final double WIND_KMH_SEVERE = 60.0;
    private static final double HEAT_C_HIGH = 43.0;
    private static final double HEAT_C_SEVERE = 48.0;
    private static final double COLD_C_LOW = 3.0;
    private static final double COLD_C_SEVERE = 0.0;

    private final ProjectRepository projectRepository;
    private final OpenMeteoClient openMeteoClient;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Weather Risk";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        ObjectNode snapshot = objectMapper.createObjectNode();
        List<AgentFindingDraft> candidates = new ArrayList<>();

        Optional<Project> maybe = projectId == null ? Optional.empty() : projectRepository.findById(projectId);
        if (maybe.isEmpty()) {
            return new GatherResult(snapshot, candidates);
        }
        Project project = maybe.get();
        if (!project.isWeatherMonitoringEnabled()
                || project.getSiteLatitude() == null || project.getSiteLongitude() == null) {
            snapshot.put("skipped", "no site location or monitoring disabled");
            return new GatherResult(snapshot, candidates);
        }

        WeatherForecast forecast =
                openMeteoClient.forecast(project.getSiteLatitude(), project.getSiteLongitude(), FORECAST_DAYS);
        if (forecast.isEmpty()) {
            snapshot.put("skipped", "forecast unavailable");
            return new GatherResult(snapshot, candidates);
        }

        String place = project.getSitePlaceLabel() != null ? project.getSitePlaceLabel() : "the site";
        snapshot.put("place", place);
        snapshot.put("timezone", forecast.timezone());
        ArrayNode daysArr = snapshot.putArray("days");
        for (DailyWx d : forecast.days()) {
            ObjectNode row = daysArr.addObject();
            row.put("date", String.valueOf(d.date()));
            row.put("rainMm", d.rainfallMm());
            row.put("windKmh", d.windMaxKmh());
            row.put("tMaxC", d.tempMaxC());
            row.put("tMinC", d.tempMinC());
            row.put("condition", d.condition());
        }

        Instant validUntil = (ctx.now() == null ? Instant.now() : ctx.now()).plus(TTL);
        List<DailyWx> days = forecast.days();

        // Rain
        List<DailyWx> rainy = filter(days, d -> d.rainfallMm() != null && d.rainfallMm() >= RAIN_MM_HIGH);
        if (!rainy.isEmpty()) {
            double maxMm = max(rainy, d -> d.rainfallMm());
            Severity sev = (maxMm >= RAIN_MM_SEVERE || rainy.size() >= 3) ? Severity.CRITICAL
                    : (maxMm >= RAIN_MM_HIGH * 1.5 || rainy.size() >= 2) ? Severity.HIGH : Severity.MEDIUM;
            candidates.add(rainRisk(projectId, place, rainy, maxMm, sev, days, validUntil));
        }

        // Wind
        List<DailyWx> windy = filter(days, d -> d.windMaxKmh() != null && d.windMaxKmh() >= WIND_KMH_HIGH);
        if (!windy.isEmpty()) {
            double maxWind = max(windy, d -> d.windMaxKmh());
            Severity sev = maxWind >= WIND_KMH_SEVERE ? Severity.CRITICAL
                    : (maxWind >= WIND_KMH_HIGH * 1.25 || windy.size() >= 2) ? Severity.HIGH : Severity.MEDIUM;
            candidates.add(windRisk(projectId, place, windy, maxWind, sev, days, validUntil));
        }

        // Heat
        List<DailyWx> hot = filter(days, d -> d.tempMaxC() != null && d.tempMaxC() >= HEAT_C_HIGH);
        if (!hot.isEmpty()) {
            double maxHeat = max(hot, d -> d.tempMaxC());
            Severity sev = maxHeat >= HEAT_C_SEVERE ? Severity.HIGH : Severity.MEDIUM;
            candidates.add(heatRisk(projectId, place, hot, maxHeat, sev, days, validUntil));
        }

        // Cold
        List<DailyWx> cold = filter(days, d -> d.tempMinC() != null && d.tempMinC() <= COLD_C_LOW);
        if (!cold.isEmpty()) {
            double minCold = min(cold, d -> d.tempMinC());
            Severity sev = minCold <= COLD_C_SEVERE ? Severity.HIGH : Severity.MEDIUM;
            candidates.add(coldRisk(projectId, place, cold, minCold, sev, days, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    // ── Finding builders ─────────────────────────────────────────────────────

    private AgentFindingDraft rainRisk(UUID projectId, String place, List<DailyWx> rainy, double maxMm,
                                       Severity severity, List<DailyWx> all, Instant validUntil) {
        DailyWx worst = rainy.stream().max((a, b) -> Double.compare(a.rainfallMm(), b.rainfallMm())).orElse(rainy.get(0));
        double confidence = forecastConfidence(all, rainy.get(0));
        return new AgentFindingDraft(
                "WEATHER_RAIN_RISK",
                "PROJECT",
                severity,
                confidence,
                "Deterministic threshold (" + fmt(RAIN_MM_HIGH) + " mm/day) over a " + all.size() + "-day Open-Meteo forecast",
                "Heavy rain forecast on " + rainy.size() + " of the next " + all.size() + " days at " + place,
                rainy.size() + " of the next " + all.size() + " forecast days exceed " + fmt(RAIN_MM_HIGH)
                        + " mm of rain; the wettest is " + worst.date() + " at " + fmt(worst.rainfallMm()) + " mm.",
                "A rain band is moving over the site during the forecast window.",
                "Heavy rain halts earthworks and concrete pours, floods excavations, softens haul roads and "
                        + "erodes exposed formation — lost working days and rework risk.",
                "Re-sequence weather-sensitive work (pours, earthworks, trenching) around the wet days; protect "
                        + "open excavations and stockpiles; confirm dewatering capacity before " + rainy.get(0).date() + ".",
                rainEvidence("Rain days over " + fmt(RAIN_MM_HIGH) + " mm", rainy.size() + " of " + all.size(),
                        "Wettest day", worst.date() + " · " + fmt(worst.rainfallMm()) + " mm", projectId, all),
                stakeholders(),
                validUntil);
    }

    private AgentFindingDraft windRisk(UUID projectId, String place, List<DailyWx> windy, double maxWind,
                                       Severity severity, List<DailyWx> all, Instant validUntil) {
        DailyWx worst = windy.stream().max((a, b) -> Double.compare(a.windMaxKmh(), b.windMaxKmh())).orElse(windy.get(0));
        double confidence = forecastConfidence(all, windy.get(0));
        return new AgentFindingDraft(
                "WEATHER_WIND_RISK",
                "PROJECT",
                severity,
                confidence,
                "Deterministic threshold (" + fmt(WIND_KMH_HIGH) + " km/h) over a " + all.size() + "-day Open-Meteo forecast",
                "High wind forecast on " + windy.size() + " of the next " + all.size() + " days at " + place,
                windy.size() + " of the next " + all.size() + " forecast days exceed " + fmt(WIND_KMH_HIGH)
                        + " km/h wind; the peak is " + worst.date() + " at " + fmt(worst.windMaxKmh()) + " km/h.",
                "A high-wind period is forecast over the site.",
                "High wind suspends crane and lifting operations, destabilises scaffolding and formwork, and grounds "
                        + "working-at-height — an HSE stop-work exposure and a productivity loss.",
                "Plan lifts and working-at-height away from the windy days; secure scaffolding, sheeting and loose "
                        + "materials; brief crane operators on the " + fmt(worst.windMaxKmh()) + " km/h peak on " + worst.date() + ".",
                buildEvidence("Wind days over " + fmt(WIND_KMH_HIGH) + " km/h", windy.size() + " of " + all.size(),
                        "Peak wind", worst.date() + " · " + fmt(worst.windMaxKmh()) + " km/h", projectId),
                stakeholders(),
                validUntil);
    }

    private AgentFindingDraft heatRisk(UUID projectId, String place, List<DailyWx> hot, double maxHeat,
                                       Severity severity, List<DailyWx> all, Instant validUntil) {
        DailyWx worst = hot.stream().max((a, b) -> Double.compare(a.tempMaxC(), b.tempMaxC())).orElse(hot.get(0));
        double confidence = forecastConfidence(all, hot.get(0));
        return new AgentFindingDraft(
                "WEATHER_HEAT_RISK",
                "PROJECT",
                severity,
                confidence,
                "Deterministic threshold (" + fmt(HEAT_C_HIGH) + " °C max) over a " + all.size() + "-day Open-Meteo forecast",
                "Extreme heat forecast on " + hot.size() + " of the next " + all.size() + " days at " + place,
                hot.size() + " of the next " + all.size() + " forecast days reach " + fmt(HEAT_C_HIGH)
                        + " °C or hotter; the peak is " + worst.date() + " at " + fmt(worst.tempMaxC()) + " °C.",
                "A heatwave is forecast over the site during the window.",
                "Extreme heat cuts labour output, forces mid-day work stoppages, accelerates concrete slump loss, and "
                        + "raises heat-stress / dehydration HSE risk.",
                "Shift labour-intensive work to early morning, enforce hydration and rest cycles, and plan concrete "
                        + "pours with retarders / night work on the hottest days (peak " + fmt(worst.tempMaxC()) + " °C on " + worst.date() + ").",
                buildEvidence("Days at/over " + fmt(HEAT_C_HIGH) + " °C", hot.size() + " of " + all.size(),
                        "Peak temperature", worst.date() + " · " + fmt(worst.tempMaxC()) + " °C", projectId),
                stakeholders(),
                validUntil);
    }

    private AgentFindingDraft coldRisk(UUID projectId, String place, List<DailyWx> cold, double minCold,
                                       Severity severity, List<DailyWx> all, Instant validUntil) {
        DailyWx worst = cold.stream().min((a, b) -> Double.compare(a.tempMinC(), b.tempMinC())).orElse(cold.get(0));
        double confidence = forecastConfidence(all, cold.get(0));
        return new AgentFindingDraft(
                "WEATHER_COLD_RISK",
                "PROJECT",
                severity,
                confidence,
                "Deterministic threshold (" + fmt(COLD_C_LOW) + " °C min) over a " + all.size() + "-day Open-Meteo forecast",
                "Near-freezing nights forecast on " + cold.size() + " of the next " + all.size() + " days at " + place,
                cold.size() + " of the next " + all.size() + " forecast days drop to " + fmt(COLD_C_LOW)
                        + " °C or colder overnight; the coldest is " + worst.date() + " at " + fmt(worst.tempMinC()) + " °C.",
                "A cold spell is forecast over the site overnight during the window.",
                "Near-freezing temperatures stall concrete curing, risk frost damage to fresh pours, and can freeze "
                        + "water lines — strength gain and durability are compromised.",
                "Protect fresh concrete with insulation / heating, delay pours on the coldest nights, and confirm "
                        + "cold-weather curing measures before " + worst.date() + " (" + fmt(worst.tempMinC()) + " °C).",
                buildEvidence("Nights at/under " + fmt(COLD_C_LOW) + " °C", cold.size() + " of " + all.size(),
                        "Coldest night", worst.date() + " · " + fmt(worst.tempMinC()) + " °C", projectId),
                stakeholders(),
                validUntil);
    }

    private List<EvidenceRef> buildEvidence(String m1Label, String m1Val, String m2Label, String m2Val, UUID projectId) {
        return List.of(
                EvidenceRef.metric(m1Label, m1Val),
                EvidenceRef.metric(m2Label, m2Val),
                EvidenceRef.entity("Site weather", "7-day forecast", "project", projectId,
                        "/projects/" + projectId + "/ai"));
    }

    /** Rain evidence + a leading COLUMN chart of the full forecast's daily rainfall. */
    private List<EvidenceRef> rainEvidence(String m1Label, String m1Val, String m2Label, String m2Val,
                                           UUID projectId, List<DailyWx> all) {
        List<EvidenceRef> ev = new ArrayList<>();
        EvidenceRef series = rainfallSeries(all);
        if (series != null) ev.add(series);
        ev.addAll(buildEvidence(m1Label, m1Val, m2Label, m2Val, projectId));
        return ev;
    }

    /** The forecast's daily rainfall (mm) as a COLUMN series, with the heavy-rain threshold as the reference line. */
    private EvidenceRef rainfallSeries(List<DailyWx> all) {
        List<EvidenceRef.Series.Point> pts = new ArrayList<>();
        for (DailyWx d : all) {
            double mm = d.rainfallMm() == null ? 0.0 : d.rainfallMm();
            pts.add(new EvidenceRef.Series.Point(dayLabel(d.date()), Math.round(mm * 10) / 10.0));
        }
        if (pts.size() < 2) return null;
        EvidenceRef.Series s = new EvidenceRef.Series("COLUMN", "mm", pts, RAIN_MM_HIGH, fmt(RAIN_MM_HIGH) + " mm");
        return EvidenceRef.chart("7-day rainfall", s);
    }

    private static String dayLabel(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private static java.util.Map<String, List<UUID>> stakeholders() {
        return java.util.Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of(), "HSE_MANAGER", List.of());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Confidence decays with how far ahead the first adverse day is: day 0 ≈ 0.85, day 6 ≈ 0.55. */
    private static double forecastConfidence(List<DailyWx> all, DailyWx firstAdverse) {
        int idx = all.indexOf(firstAdverse);
        if (idx < 0) idx = 0;
        return Math.max(0.5, 0.85 - idx * 0.05);
    }

    private static List<DailyWx> filter(List<DailyWx> days, Predicate<DailyWx> p) {
        List<DailyWx> out = new ArrayList<>();
        for (DailyWx d : days) {
            if (p.test(d)) out.add(d);
        }
        return out;
    }

    private static double max(List<DailyWx> days, ToDoubleFunction<DailyWx> f) {
        double m = Double.NEGATIVE_INFINITY;
        for (DailyWx d : days) m = Math.max(m, f.applyAsDouble(d));
        return m;
    }

    private static double min(List<DailyWx> days, ToDoubleFunction<DailyWx> f) {
        double m = Double.POSITIVE_INFINITY;
        for (DailyWx d : days) m = Math.min(m, f.applyAsDouble(d));
        return m;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }
}
