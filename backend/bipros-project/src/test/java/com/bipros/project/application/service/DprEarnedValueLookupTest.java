package com.bipros.project.application.service;

import com.bipros.project.domain.repository.DailyProgressReportRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DprEarnedValueLookupTest {

    @Test
    void mapsRowsByDateAndMergesDuplicates() {
        var repo = mock(DailyProgressReportRepository.class);
        UUID pid = UUID.randomUUID();
        LocalDate d1 = LocalDate.of(2026, 1, 10);
        LocalDate d2 = LocalDate.of(2026, 2, 1);
        when(repo.sumEarnedValueGroupedByDate(pid)).thenReturn(List.of(
            new Object[]{ d1, new BigDecimal("100") },
            new Object[]{ d1, new BigDecimal("50") },   // defensive merge
            new Object[]{ d2, new BigDecimal("200") }
        ));

        Map<LocalDate, BigDecimal> out = new DprEarnedValueLookup(repo).sumByProjectGroupedByDate(pid);

        assertThat(out.get(d1)).isEqualByComparingTo("150");
        assertThat(out.get(d2)).isEqualByComparingTo("200");
        assertThat(out).hasSize(2);
    }

    @Test
    void nullProjectIdReturnsEmpty() {
        var repo = mock(DailyProgressReportRepository.class);
        assertThat(new DprEarnedValueLookup(repo).sumByProjectGroupedByDate(null)).isEmpty();
    }
}
