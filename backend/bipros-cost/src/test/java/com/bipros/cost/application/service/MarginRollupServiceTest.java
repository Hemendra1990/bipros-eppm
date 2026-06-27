package com.bipros.cost.application.service;

import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.dto.MarginActivityDto;
import com.bipros.cost.application.dto.MarginItemDto;
import com.bipros.cost.application.dto.MarginPeriodDto;
import com.bipros.cost.application.dto.MarginSummaryDto;
import com.bipros.cost.application.dto.PeriodPerformanceRollupDto;
import com.bipros.project.application.dto.DailyCostReportResponse;
import com.bipros.project.application.dto.DailyCostReportRow;
import com.bipros.project.application.service.DailyCostReportService;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarginRollupServiceTest {

    private static final UUID PID = UUID.randomUUID();
    private static final UUID ITEM_A = UUID.randomUUID();
    private static final UUID ITEM_B = UUID.randomUUID();
    private static final UUID D1 = UUID.randomUUID();
    private static final UUID D2 = UUID.randomUUID();

    @Mock BoqItemRepository boqItemRepository;
    @Mock DailyCostReportService dailyCostReportService;
    @Mock DprActualCostLookup dprActualCostLookup;
    @Mock CostService costService;
    @Mock PerformanceRollupService performanceRollupService;
    @InjectMocks MarginRollupService service;

    private BoqItem itemA;
    private BoqItem itemB;

    @BeforeEach
    void setUp() {
        itemA = BoqItem.builder().itemNo("1").description("Excavation work").unit("cu.m")
                .budgetedRate(new BigDecimal("10")).boqRate(new BigDecimal("12"))
                .qtyExecutedToDate(new BigDecimal("100")).build();
        itemB = BoqItem.builder().itemNo("2").description("Idle item").unit("nr")
                .budgetedRate(new BigDecimal("5")).boqRate(new BigDecimal("6"))
                .qtyExecutedToDate(BigDecimal.ZERO).build();
        ReflectionTestUtils.setField(itemA, "id", ITEM_A);
        ReflectionTestUtils.setField(itemB, "id", ITEM_B);

        when(boqItemRepository.findByProjectIdOrderByItemNoAsc(PID)).thenReturn(List.of(itemA, itemB));
        when(dailyCostReportService.generate(PID, null, null)).thenReturn(new DailyCostReportResponse(
                null, null,
                List.of(
                        row(D1, LocalDate.of(2026, 1, 10), "Excavation", ITEM_A, "60"),
                        row(D2, LocalDate.of(2026, 2, 15), "Excavation", ITEM_A, "40")),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(dprActualCostLookup.sumByDpr(PID)).thenReturn(Map.of(
                D1, new BigDecimal("600"), D2, new BigDecimal("400")));            // Σ = 1000
        when(costService.getCostSummary(PID)).thenReturn(
                CostSummaryDto.of(BigDecimal.ZERO, new BigDecimal("1200"), BigDecimal.ZERO, BigDecimal.ZERO, 0));
        when(performanceRollupService.rollup(PID, "M")).thenReturn(List.of(
                period("Jan 2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "720"),
                period("Feb 2026", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "480")));
    }

    @Test
    void summaryActualCostIsTotalActualAndRevenueFromRows() {
        MarginSummaryDto s = service.summary(PID, BoqItem::getBudgetedRate);
        assertThat(s.actualCost()).isEqualByComparingTo("1200");   // canonical totalActual
        assertThat(s.revenue()).isEqualByComparingTo("1000");      // 60*10 + 40*10
        assertThat(s.margin()).isEqualByComparingTo("-200");
        assertThat(s.marginPct()).isEqualByComparingTo("-0.2");
    }

    @Test
    void itemsFootToTotalActualWithOtherRow() {
        List<MarginItemDto> items = service.items(PID, BoqItem::getBudgetedRate);
        BigDecimal sumCost = items.stream().map(MarginItemDto::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumRev = items.stream().map(MarginItemDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumCost).isEqualByComparingTo("1200");          // == summary AC
        assertThat(sumRev).isEqualByComparingTo("1000");           // == summary revenue

        MarginItemDto a = items.stream().filter(i -> ITEM_A.equals(i.boqItemId())).findFirst().orElseThrow();
        assertThat(a.actualCost()).isEqualByComparingTo("1000");
        assertThat(a.revenue()).isEqualByComparingTo("1000");

        MarginItemDto other = items.stream().filter(i -> i.boqItemId() == null).findFirst().orElseThrow();
        assertThat(other.description()).isEqualTo(MarginRollupService.OTHER_ROW_LABEL);
        assertThat(other.actualCost()).isEqualByComparingTo("200");
        assertThat(other.revenue()).isEqualByComparingTo("0");
    }

    @Test
    void activitiesFootToTotalActualWithOtherRow() {
        List<MarginActivityDto> acts = service.activities(PID, BoqItem::getBudgetedRate);
        BigDecimal sumCost = acts.stream().map(MarginActivityDto::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumCost).isEqualByComparingTo("1200");
        MarginActivityDto exc = acts.stream().filter(x -> x.activity().equals("Excavation")).findFirst().orElseThrow();
        assertThat(exc.actualCost()).isEqualByComparingTo("1000");
        assertThat(exc.revenue()).isEqualByComparingTo("1000");
        MarginActivityDto other = acts.stream().filter(x -> x.activity().equals(MarginRollupService.OTHER_ROW_LABEL))
                .findFirst().orElseThrow();
        assertThat(other.actualCost()).isEqualByComparingTo("200");
    }

    @Test
    void periodsReusePerformanceAcAndBucketRevenue() {
        List<MarginPeriodDto> periods = service.periods(PID, "M", BoqItem::getBudgetedRate);
        assertThat(periods).hasSize(2);
        BigDecimal sumAc = periods.stream().map(MarginPeriodDto::actualCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumRev = periods.stream().map(MarginPeriodDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumAc).isEqualByComparingTo("1200");            // == totalActual (Performance parity)
        assertThat(sumRev).isEqualByComparingTo("1000");
        MarginPeriodDto jan = periods.get(0);
        assertThat(jan.actualCost()).isEqualByComparingTo("720");
        assertThat(jan.revenue()).isEqualByComparingTo("600");     // row D1 (60*10) falls in Jan
    }

    @Test
    void boqScopeUsesBoqRate() {
        MarginSummaryDto s = service.summary(PID, BoqItem::getBoqRate);
        assertThat(s.revenue()).isEqualByComparingTo("1200");      // 100 * 12
        MarginItemDto a = service.items(PID, BoqItem::getBoqRate).stream()
                .filter(i -> ITEM_A.equals(i.boqItemId())).findFirst().orElseThrow();
        assertThat(a.revenue()).isEqualByComparingTo("1200");
    }

    @Test
    void costCountedOncePerDprEvenWhenDprSpansMultipleRows() {
        // One DPR (D1, total line_cost 600) appearing as two rows (ITEM_A and ITEM_B).
        when(dailyCostReportService.generate(PID, null, null)).thenReturn(new DailyCostReportResponse(
                null, null,
                List.of(
                        row(D1, java.time.LocalDate.of(2026, 1, 10), "Excavation", ITEM_A, "30"),
                        row(D1, java.time.LocalDate.of(2026, 1, 10), "Excavation", ITEM_B, "30")),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        when(dprActualCostLookup.sumByDpr(PID)).thenReturn(Map.of(D1, new java.math.BigDecimal("600")));
        when(costService.getCostSummary(PID)).thenReturn(
                CostSummaryDto.of(java.math.BigDecimal.ZERO, new java.math.BigDecimal("800"),
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0));

        List<MarginItemDto> items = service.items(PID, BoqItem::getBudgetedRate);
        java.math.BigDecimal sumCost = items.stream().map(MarginItemDto::actualCost)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(sumCost).isEqualByComparingTo("800");   // D1's 600 counted once, not 1200
        MarginItemDto other = items.stream().filter(i -> i.boqItemId() == null).findFirst().orElseThrow();
        assertThat(other.actualCost()).isEqualByComparingTo("200");
    }

    @Test
    void periodRevenueFootsToSummaryEvenWithOutOfWindowRows() {
        // A third revenue-bearing row dated March 2026 — outside both mocked Jan/Feb buckets.
        when(dailyCostReportService.generate(PID, null, null)).thenReturn(new DailyCostReportResponse(
                null, null,
                List.of(
                        row(D1, java.time.LocalDate.of(2026, 1, 10), "Excavation", ITEM_A, "60"),
                        row(D2, java.time.LocalDate.of(2026, 2, 15), "Excavation", ITEM_A, "40"),
                        row(java.util.UUID.randomUUID(), java.time.LocalDate.of(2026, 3, 20), "Excavation", ITEM_A, "20")),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));

        MarginSummaryDto summary = service.summary(PID, BoqItem::getBudgetedRate);  // (60+40+20)*10 = 1200
        java.math.BigDecimal sumPeriodRev = service.periods(PID, "M", BoqItem::getBudgetedRate).stream()
                .map(MarginPeriodDto::revenue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertThat(sumPeriodRev).isEqualByComparingTo(summary.revenue());  // 1200, not 1000 (March row not dropped)
    }

    private static DailyCostReportRow row(UUID dprId, LocalDate date, String activity, UUID boqItemId, String qty) {
        return new DailyCostReportRow(dprId, date, activity, new BigDecimal(qty), "cu.m", boqItemId, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static PeriodPerformanceRollupDto period(String name, LocalDate start, LocalDate end, String ac) {
        return new PeriodPerformanceRollupDto(UUID.randomUUID(), name, "MONTHLY", start, end,
                new BigDecimal(ac), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
}
