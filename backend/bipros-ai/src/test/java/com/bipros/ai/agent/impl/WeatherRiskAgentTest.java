package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.integration.adapter.weather.DailyWx;
import com.bipros.integration.adapter.weather.OpenMeteoClient;
import com.bipros.integration.adapter.weather.WeatherForecast;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherRiskAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private OpenMeteoClient openMeteoClient;

    private WeatherRiskAgent agent() {
        return new WeatherRiskAgent(projectRepository, openMeteoClient, new ObjectMapper());
    }

    private static Project enabledProject() {
        Project p = new Project();
        p.setSiteLatitude(26.18);
        p.setSiteLongitude(56.24);
        p.setSitePlaceLabel("Khasab, Musandam, Oman");
        p.setWeatherMonitoringEnabled(true);
        return p;
    }

    private static DailyWx day(int offset, double rain, double wind, double tMax, double tMin) {
        return new DailyWx(LocalDate.of(2026, 7, 9).plusDays(offset), tMax, tMin, rain, wind, 61, "Rain");
    }

    @Test
    void emitsRainWindHeatFindingsSortedBySeverity() {
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(enabledProject()));
        WeatherForecast forecast = new WeatherForecast(26.18, 56.24, "Asia/Muscat", List.of(
                day(0, 5, 10, 30, 20),    // benign
                day(1, 60, 30, 32, 21),   // severe rain (>=50 mm)
                day(2, 25, 55, 44, 22),   // heavy rain + high wind (>=50) + heat (>=43)
                day(3, 2, 12, 35, 20),
                day(4, 0, 8, 33, 19),
                day(5, 1, 9, 31, 18),
                day(6, 0, 7, 30, 17)));
        when(openMeteoClient.forecast(26.18, 56.24, 7)).thenReturn(forecast);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("WEATHER_RAIN_RISK", "WEATHER_WIND_RISK", "WEATHER_HEAT_RISK");
        // Rain is CRITICAL (max 60mm >= severe) and sorts first.
        assertThat(c.get(0).findingType()).isEqualTo("WEATHER_RAIN_RISK");
        assertThat(c.get(0).severity()).isEqualTo(Severity.CRITICAL);
        assertThat(c).allSatisfy(f -> assertThat(f.confidence()).isBetween(0.5, 0.9));
        assertThat(result.dataSnapshot().get("days").size()).isEqualTo(7);
    }

    @Test
    void skipsWhenMonitoringDisabled() {
        Project disabled = enabledProject();
        disabled.setWeatherMonitoringEnabled(false);
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(disabled));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void skipsWhenNoCoordinates() {
        Project noCoords = new Project();
        noCoords.setWeatherMonitoringEnabled(true);
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(noCoords));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void benignForecastYieldsNoFindings() {
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(enabledProject()));
        WeatherForecast mild = new WeatherForecast(26.18, 56.24, "Asia/Muscat", List.of(
                day(0, 1, 10, 30, 20),
                day(1, 0, 12, 31, 21),
                day(2, 2, 15, 32, 22)));
        lenient().when(openMeteoClient.forecast(26.18, 56.24, 7)).thenReturn(mild);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("days").size()).isEqualTo(3);
    }
}
