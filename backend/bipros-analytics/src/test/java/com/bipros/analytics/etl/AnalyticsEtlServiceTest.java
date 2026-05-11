package com.bipros.analytics.etl;

import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage for the per-dim upsert paths on {@link AnalyticsEtlService}.
 * Focuses on:
 * <ul>
 *   <li>Exactly one INSERT issued per upsert call.</li>
 *   <li>SQL matches the template constants in {@link AnalyticsDimensionSql}.</li>
 *   <li>{@code _version} is {@code System.currentTimeMillis()}-class (live writes
 *       always beat the nightly batch).</li>
 * </ul>
 */
class AnalyticsEtlServiceTest {

    private ClickHouseTemplate clickHouse;
    private AnalyticsEtlService etl;

    @BeforeEach
    void setUp() {
        clickHouse = mock(ClickHouseTemplate.class);
        when(clickHouse.execute(anyString(), anyMap())).thenReturn(1);
        etl = new AnalyticsEtlService(clickHouse);
    }

    @Test
    void upsertProjectDimensionIssuesOneInsertWithLiveVersion() {
        long before = System.currentTimeMillis();

        Project p = new Project();
        p.setId(UUID.randomUUID());
        p.setCode("ROAD-001");
        p.setName("Test Road");
        p.setStatus(ProjectStatus.PLANNED);
        p.setPlannedStartDate(LocalDate.of(2026, 1, 1));
        p.setPlannedFinishDate(LocalDate.of(2027, 1, 1));

        etl.upsertProjectDimension(p);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCap =
                ArgumentCaptor.forClass(Map.class);
        verify(clickHouse, times(1)).execute(sqlCap.capture(), paramsCap.capture());

        long after = System.currentTimeMillis();

        assertThat(sqlCap.getValue()).isEqualTo(AnalyticsDimensionSql.INSERT_PROJECT);
        Map<String, Object> params = paramsCap.getValue();
        assertThat(params).containsEntry("projectId", p.getId());
        assertThat(params).containsEntry("code", "ROAD-001");
        assertThat(params).containsEntry("name", "Test Road");
        assertThat(params).containsEntry("status", "PLANNED");
        assertThat(params).containsEntry("currency", "INR");

        long version = ((Number) params.get("version")).longValue();
        assertThat(version)
                .as("live _version must be wall-clock millis so it beats batch VERSION fixed at JVM start")
                .isBetween(before, after);
    }

    @Test
    void nowVersionIsMonotonicWithSystemClock() {
        long before = System.currentTimeMillis();
        long v = etl.nowVersion();
        long after = System.currentTimeMillis();
        assertThat(v).isBetween(before, after);
    }
}
