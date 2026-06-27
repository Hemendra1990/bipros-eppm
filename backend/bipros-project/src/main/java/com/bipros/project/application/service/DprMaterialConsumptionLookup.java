package com.bipros.project.application.service;

import com.bipros.project.application.dto.DprMaterialLine;
import com.bipros.project.domain.repository.DprMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module read of approved DPR material consumption lines, used by the Material Consumption
 * Report so projects that record material via DPRs (rather than the resource material ledger) are
 * not blank. Mirrors {@code DprActualCostLookup}: the DPR repositories stay encapsulated here and
 * the caller receives a neutral DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DprMaterialConsumptionLookup {

    private final DprMaterialRepository repository;

    public List<DprMaterialLine> findApprovedLines(UUID projectId, LocalDate from, LocalDate to) {
        if (projectId == null || from == null || to == null) return List.of();
        List<Object[]> rows = repository.findApprovedMaterialLines(projectId, from, to);
        List<DprMaterialLine> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new DprMaterialLine(
                (LocalDate) r[0],
                (UUID) r[1],
                (String) r[2],
                (String) r[3],
                toBig(r[4]),
                toBig(r[5]),
                toBig(r[6])));
        }
        return out;
    }

    private static BigDecimal toBig(Object o) {
        if (o == null) return null;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
