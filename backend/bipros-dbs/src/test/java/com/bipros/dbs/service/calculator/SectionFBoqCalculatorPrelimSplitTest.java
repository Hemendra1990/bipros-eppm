package com.bipros.dbs.service.calculator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Phase 7: verifies {@link SectionFBoqCalculator} buckets each BOQ line into
 * {@code directBoqAmount} or {@code prelimBoqAmount} according to the underlying
 * activity's {@code is_preliminary} flag returned by the SQL projection.
 *
 * <p>The calculator runs a native query against {@code project.daily_progress_reports
 * JOIN project.boq_items LEFT JOIN activity.activities}. We mock the {@link EntityManager}
 * to return a synthetic two-row result — one non-preliminary, one preliminary — and assert
 * the resulting {@link BoqSectionResult} splits the per-day amount correctly.
 */
@ExtendWith(MockitoExtension.class)
class SectionFBoqCalculatorPrelimSplitTest {

    @Mock private EntityManager em;
    @Mock private Query nativeQuery;

    @Test
    @DisplayName("compute splits forTheDayAmount into directBoqAmount vs prelimBoqAmount by is_preliminary")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void compute_splitsDirectAndPrelim() throws Exception {
        SectionFBoqCalculator calc = new SectionFBoqCalculator();
        injectEntityManager(calc, em);

        UUID projectId = UUID.randomUUID();
        UUID supId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 18);

        UUID boq1 = UUID.randomUUID();
        UUID boq2 = UUID.randomUUID();

        // Row 0: non-prelim activity. itemNo, description, unit, rate, qtyToday, plannedAmount,
        //        qtyToDate, boqId, isPreliminary.
        //   amount today = 10 × 100 = 1000.00
        Object[] direct = new Object[]{
            "BOQ-001", "Earthwork in cutting", "cum",
            new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("50000"),
            new BigDecimal("100"), boq1, Boolean.FALSE
        };
        // Row 1: prelim activity (Mobilization).
        //   amount today = 1 × 250 = 250.00
        Object[] prelim = new Object[]{
            "BOQ-100", "Mobilisation", "LS",
            new BigDecimal("250"), new BigDecimal("1"), new BigDecimal("5000"),
            new BigDecimal("2"), boq2, Boolean.TRUE
        };

        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn((List) List.of(direct, prelim));

        BoqSectionResult result = calc.compute(projectId, supId, date);

        // forTheDay totals 1000 + 250 = 1250
        assertThat(result.forTheDayAmount()).isEqualByComparingTo("1250.00");
        // Direct bucket gets only the non-preliminary line's amount.
        assertThat(result.directBoqAmount()).isEqualByComparingTo("1000.00");
        // Prelim bucket gets only the preliminary line's amount.
        assertThat(result.prelimBoqAmount()).isEqualByComparingTo("250.00");
        // Direct + Prelim = ForTheDay (round-trip invariant).
        assertThat(result.directBoqAmount().add(result.prelimBoqAmount()))
            .isEqualByComparingTo(result.forTheDayAmount());

        // planned-to-date = 50000 + 5000 (distinct BOQ ids only counted once each)
        assertThat(result.plannedAmount()).isEqualByComparingTo("55000.00");
        // achieved-to-date = 100×100 + 2×250 = 10000 + 500 = 10500
        assertThat(result.achievedAmount()).isEqualByComparingTo("10500.00");

        // The flat line list always carries every DPR row (no de-dupe).
        assertThat(result.lines()).hasSize(2);
    }

    @Test
    @DisplayName("compute returns empty result when EM throws — falls back gracefully")
    void compute_emptyOnException() {
        SectionFBoqCalculator calc = new SectionFBoqCalculator();
        // No EntityManager wired — NPE inside compute -> caught -> empty.
        BoqSectionResult result = calc.compute(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        assertThat(result.forTheDayAmount()).isEqualByComparingTo("0");
        assertThat(result.directBoqAmount()).isEqualByComparingTo("0");
        assertThat(result.prelimBoqAmount()).isEqualByComparingTo("0");
        assertThat(result.lines()).isEmpty();
    }

    private static void injectEntityManager(SectionFBoqCalculator target, EntityManager em) throws Exception {
        // SectionFBoqCalculator declares `private EntityManager em` annotated with
        // @PersistenceContext. In a unit test we reach in via reflection so we don't have
        // to bring up a Spring container just to assert the split arithmetic.
        Field f = SectionFBoqCalculator.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(target, em);
    }
}
