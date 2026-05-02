package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IC-PMS M3 — seeds 3 monthly material reconciliations (Feb/Mar/Apr 2026) for
 * every MATERIAL resource = ~30 rows for the 10 Phase-D materials.
 *
 * <p>Balances obey the accounting identity
 * {@code closing = opening + received - consumed - wastage}, with wastage held
 * at ~1–3% of consumption. Opening balance of month N+1 equals closing of N.
 * Idempotent: skipped entirely if any reconciliations already exist.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(112)
@RequiredArgsConstructor
public class IcpmsMaterialReconciliationSeeder implements CommandLineRunner {

    /** Monthly periods seeded (YYYY-MM format used by the period column, length 10). */
    private static final String[] PERIODS = {"2026-02", "2026-03", "2026-04"};

    private final MaterialReconciliationRepository materialReconciliationRepository;
    private final ResourceRepository resourceRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (materialReconciliationRepository.count() > 0) {
            log.info("[IC-PMS Material Reconciliations] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Material Reconciliations] DMIC-PROG project not found — run Phase A first");
            return;
        }

        List<Resource> materials = resourceRepository.findByResourceType_Code("MATERIAL");
        if (materials.isEmpty()) {
            log.warn("[IC-PMS Material Reconciliations] no MATERIAL resources found — run Phase D first");
            return;
        }

        int rows = 0;
        int mIdx = 0;
        for (Resource mat : materials) {
            // Base monthly flow sized off the pool — ~20% of pool moves per month.
            // Pool sizing now uses availability (% allocation) as a proxy since the
            // legacy pool_max_available column is gone; fall back to a sensible default.
            double poolMax = mat.getAvailability() != null
                ? Math.max(1.0, mat.getAvailability().doubleValue() * 10.0)
                : 1000.0;
            double monthlyReceived = Math.max(10.0, poolMax * 0.25);
            double monthlyConsumed = Math.max(8.0,  poolMax * 0.22);
            double opening = Math.max(5.0, poolMax * 0.15);
            String unit = resolveUnitLabel(mat);

            for (int p = 0; p < PERIODS.length; p++) {
                // Slight month-over-month growth: +5% received, +6% consumed
                double received = round(monthlyReceived * (1.0 + 0.05 * p));
                double consumed = round(monthlyConsumed * (1.0 + 0.06 * p));
                // Wastage: 1% + (mIdx % 3) * 1% of consumed (so 1–3%).
                double wastagePct = 0.01 + (mIdx % 3) * 0.01;
                double wastage = round(consumed * wastagePct);
                double closing = round(opening + received - consumed - wastage);
                if (closing < 0) {
                    // Defensive clamp — nudge received up to keep the identity positive.
                    received = round(received + Math.abs(closing) + 5.0);
                    closing = round(opening + received - consumed - wastage);
                }

                MaterialReconciliation row = MaterialReconciliation.builder()
                    .resourceId(mat.getId())
                    .projectId(programme.getId())
                    .period(PERIODS[p])
                    .openingBalance(opening)
                    .received(received)
                    .consumed(consumed)
                    .wastage(wastage)
                    .closingBalance(closing)
                    .unit(unit)
                    .remarks(null)
                    .build();
                materialReconciliationRepository.save(row);
                rows++;

                // Roll forward: next month's opening = this month's closing.
                opening = closing;
            }
            mIdx++;
        }

        log.info("[IC-PMS Material Reconciliations] seeded {} rows ({} materials × {} periods)",
            rows, materials.size(), PERIODS.length);
    }

    private static String resolveUnitLabel(Resource mat) {
        String unit = mat.getUnit();
        return unit != null && !unit.isBlank() ? unit : "MT";
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
