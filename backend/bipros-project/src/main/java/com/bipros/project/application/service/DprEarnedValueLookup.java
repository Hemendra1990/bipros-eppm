package com.bipros.project.application.service;

import com.bipros.project.domain.repository.DailyProgressReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DprEarnedValueLookup {

    private final DailyProgressReportRepository dprRepository;

    /**
     * Approved DPR earned value grouped by report date, with cumulative capping:
     * once a BOQ item's running executed quantity crosses boqQty, later days contribute 0.
     *
     * <p>Stage 4 (B2): computed over MEASURED rows only (measurement-operation / pre-split /
     * partition rows) so a split line's non-measure operations don't distort the date shape.
     * If that filter would empty the shape entirely while unfiltered rows exist (extreme case:
     * every attributed DPR is non-measure), fall back to the unfiltered shape — a distorted
     * shape scales to the right total, an empty one flatlines the Performance tab.
     */
    public Map<LocalDate, BigDecimal> sumByProjectGroupedByDate(UUID projectId) {
        if (projectId == null) return new HashMap<>();
        Map<LocalDate, BigDecimal> out = aggregate(dprRepository.sumQtyByBoqItemAndDateMeasured(projectId));
        if (out.isEmpty()) {
            out = aggregate(dprRepository.sumQtyByBoqItemAndDate(projectId));
        }
        return out;
    }

    private static Map<LocalDate, BigDecimal> aggregate(List<Object[]> raw) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();

        // group rows by BOQ item
        Map<UUID, List<Object[]>> byItem = new HashMap<>();
        Map<UUID, BigDecimal[]> meta = new HashMap<>(); // itemId -> [boqQty, budgetedRate]
        for (Object[] r : raw) {
            UUID itemId = (UUID) r[0];
            byItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(r);
            meta.putIfAbsent(itemId, new BigDecimal[]{(BigDecimal) r[3], (BigDecimal) r[4]});
        }

        for (Map.Entry<UUID, List<Object[]>> e : byItem.entrySet()) {
            BigDecimal boqQty = meta.get(e.getKey())[0];
            BigDecimal rate = meta.get(e.getKey())[1];
            List<Object[]> rows = e.getValue();
            rows.sort(Comparator.comparing(r -> (LocalDate) r[1]));
            BigDecimal cum = BigDecimal.ZERO;
            for (Object[] r : rows) {
                LocalDate date = (LocalDate) r[1];
                BigDecimal qty = r[2] == null ? BigDecimal.ZERO : (BigDecimal) r[2];
                BigDecimal prevCapped = cum.min(boqQty);
                cum = cum.add(qty);
                BigDecimal cappedCum = cum.min(boqQty);
                BigDecimal periodEarned = cappedCum.subtract(prevCapped).multiply(rate);
                out.merge(date, periodEarned, BigDecimal::add);
            }
        }
        return out;
    }
}
