package com.bipros.project.application.service;

import com.bipros.project.domain.repository.DailyProgressReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DprEarnedValueLookup {

    private final DailyProgressReportRepository dprRepository;

    /** Approved DPR earned value (qtyExecuted × BOQ budgetedRate) grouped by report date. */
    public Map<LocalDate, BigDecimal> sumByProjectGroupedByDate(UUID projectId) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        if (projectId == null) return out;
        for (Object[] row : dprRepository.sumEarnedValueGroupedByDate(projectId)) {
            if (row[0] == null) continue;
            LocalDate date = (LocalDate) row[0];
            BigDecimal ev = row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1];
            out.merge(date, ev, BigDecimal::add);
        }
        return out;
    }
}
