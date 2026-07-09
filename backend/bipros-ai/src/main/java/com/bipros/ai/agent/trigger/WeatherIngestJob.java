package com.bipros.ai.agent.trigger;

import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.integration.adapter.weather.DailyWx;
import com.bipros.integration.adapter.weather.OpenMeteoClient;
import com.bipros.integration.adapter.weather.WeatherForecast;
import com.bipros.project.domain.model.DailyWeather;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import com.bipros.project.domain.repository.DailyWeatherRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job that pulls today's weather from Open-Meteo for every weather-enabled project and
 * upserts it into the DPR "Section C — Weather" log ({@link DailyWeather}). This auto-populates the
 * daily weather readings that a supervisor would otherwise type in by hand.
 *
 * <p>Lease-guarded (mirrors {@link AgentSweepJobs}). <b>Insert-if-absent</b>: if a row already exists
 * for the project+date (a supervisor's manual entry, or a previous run today), it is left untouched —
 * manual data always wins. Any provider failure is logged per-project and skipped; one bad site never
 * aborts the sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherIngestJob {

    private static final String AUTO_REMARK = "Auto-filled from Open-Meteo";

    private final ProjectRepository projectRepository;
    private final DailyWeatherRepository weatherRepository;
    private final OpenMeteoClient openMeteoClient;
    private final ScheduledJobLeaseRepository leaseRepository;

    @Scheduled(cron = "${bipros.agent.schedule.weather-ingest-cron}")
    public void ingest() {
        if (!acquire("agent_weather_ingest")) {
            return;
        }
        List<Project> projects = projectRepository.findByStatus(ProjectStatus.ACTIVE, Pageable.unpaged()).getContent();
        int written = 0;
        for (Project p : projects) {
            if (!p.isWeatherMonitoringEnabled() || p.getSiteLatitude() == null || p.getSiteLongitude() == null) {
                continue;
            }
            try {
                if (upsertToday(p)) {
                    written++;
                }
            } catch (Exception ex) {
                log.warn("WeatherIngestJob failed for project {}: {}", p.getId(), ex.getMessage());
            }
        }
        log.info("WeatherIngestJob wrote {} daily-weather rows", written);
    }

    /** Fetch day-0 weather and insert it if no row exists yet for that (project, date). */
    private boolean upsertToday(Project p) {
        WeatherForecast forecast = openMeteoClient.forecast(p.getSiteLatitude(), p.getSiteLongitude(), 1);
        if (forecast.isEmpty()) {
            return false;
        }
        DailyWx today = forecast.days().get(0);
        LocalDate date = today.date() != null ? today.date() : LocalDate.now();

        if (weatherRepository.findByProjectIdAndLogDate(p.getId(), date).isPresent()) {
            return false; // manual entry or earlier auto-run wins — never clobber.
        }

        DailyWeather row = DailyWeather.builder()
                .projectId(p.getId())
                .logDate(date)
                .tempMaxC(today.tempMaxC())
                .tempMinC(today.tempMinC())
                .rainfallMm(today.rainfallMm())
                .windKmh(today.windMaxKmh())
                .weatherCondition(today.condition())
                .remarks(AUTO_REMARK)
                .build();
        weatherRepository.save(row);
        return true;
    }

    private boolean acquire(String jobName) {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofMinutes(10));
        String owner = "node-" + UUID.randomUUID();
        return leaseRepository.tryAcquire(jobName, until, now, owner) != 0;
    }
}
