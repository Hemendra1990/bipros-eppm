package com.bipros.project.application.service;

import com.bipros.project.application.dto.DprMaterialLine;
import com.bipros.project.domain.repository.DprMaterialRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DprMaterialConsumptionLookupTest {

    @Test
    void mapsApprovedMaterialLines() {
        var repo = mock(DprMaterialRepository.class);
        UUID pid = UUID.randomUUID();
        UUID act = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 3, 19);
        LocalDate to = LocalDate.of(2026, 6, 5);
        LocalDate d = LocalDate.of(2026, 3, 19);
        when(repo.findApprovedMaterialLines(pid, from, to)).thenReturn(List.of(
            new Object[]{ d, act, "Concrete", "m3", new BigDecimal("9.000"), new BigDecimal("62.0000"), new BigDecimal("558.00") },
            new Object[]{ d, act, "Concrete", "m3", new BigDecimal("6.000"), new BigDecimal("62.0000"), new BigDecimal("372.00") }
        ));

        List<DprMaterialLine> out = new DprMaterialConsumptionLookup(repo).findApprovedLines(pid, from, to);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).reportDate()).isEqualTo(d);
        assertThat(out.get(0).activityId()).isEqualTo(act);
        assertThat(out.get(0).materialName()).isEqualTo("Concrete");
        assertThat(out.get(0).unit()).isEqualTo("m3");
        assertThat(out.get(0).quantity()).isEqualByComparingTo("9");
        assertThat(out.get(0).unitRate()).isEqualByComparingTo("62");
        assertThat(out.get(0).lineCost()).isEqualByComparingTo("558");
        assertThat(out.get(1).lineCost()).isEqualByComparingTo("372");
    }

    @Test
    void nullArgsReturnEmpty() {
        var repo = mock(DprMaterialRepository.class);
        assertThat(new DprMaterialConsumptionLookup(repo).findApprovedLines(null, LocalDate.now(), LocalDate.now())).isEmpty();
        assertThat(new DprMaterialConsumptionLookup(repo).findApprovedLines(UUID.randomUUID(), null, LocalDate.now())).isEmpty();
    }
}
