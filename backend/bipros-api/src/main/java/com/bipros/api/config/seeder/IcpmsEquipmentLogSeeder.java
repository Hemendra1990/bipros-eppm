package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.EquipmentStatus;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * IC-PMS M3 — seeds daily equipment deployment logs for 10 NONLABOR resources
 * across a 14-day window (2026-04-01 → 2026-04-14).
 *
 * <p>Each log records operating/idle/breakdown hours, the operator, and the
 * deployment site. Idempotent: skipped entirely if any equipment logs already
 * exist in the table.
 */
@Slf4j
@Component
@Profile("dev")
@Order(110)
@RequiredArgsConstructor
public class IcpmsEquipmentLogSeeder implements CommandLineRunner {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 4, 1);
    private static final int WINDOW_DAYS = 14;

    private final EquipmentLogRepository equipmentLogRepository;
    private final ResourceRepository resourceRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (equipmentLogRepository.count() > 0) {
            log.info("[IC-PMS Equipment Logs] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Equipment Logs] DMIC-PROG project not found — run Phase A first");
            return;
        }

        List<Resource> equipment = resourceRepository.findByResourceType_Code("EQUIPMENT");
        if (equipment.isEmpty()) {
            log.warn("[IC-PMS Equipment Logs] no EQUIPMENT resources found — run Phase D first");
            return;
        }

        // Deterministic per-resource parameterisation — stable across re-runs.
        String[] sites = {
            "Dholera Zone A", "Dholera Zone B", "Dighi Port Zone 1",
            "Shendra MIDC", "Vikram Udyogpuri", "Pithampur Sector C",
            "Greater Noida TOD", "Ambernath GIDC"
        };

        int rows = 0;
        int eIdx = 0;
        for (Resource eq : equipment) {
            int operatorNum = (eIdx % 20) + 1;
            String operator = "L&T Operator " + operatorNum;
            String site = sites[eIdx % sites.length];
            for (int d = 0; d < WINDOW_DAYS; d++) {
                LocalDate logDate = WINDOW_START.plusDays(d);
                // Cycle reasonable values: 6-8 operating, 0-2 idle, 0-0.5 breakdown.
                double operating = 6.0 + ((eIdx + d) % 3);              // 6, 7, 8
                double idle = (d % 3 == 0) ? 1.0 : ((d % 5 == 0) ? 2.0 : 0.0);
                double breakdown = (d == 7 && eIdx % 4 == 0) ? 0.5 : 0.0;
                EquipmentStatus status = breakdown > 0
                    ? EquipmentStatus.BREAKDOWN
                    : (idle >= 2.0 ? EquipmentStatus.IDLE : EquipmentStatus.WORKING);
                double fuel = operating * 12.5;  // rough litres/hour estimate

                EquipmentLog row = EquipmentLog.builder()
                    .resourceId(eq.getId())
                    .projectId(programme.getId())
                    .logDate(logDate)
                    .deploymentSite(site)
                    .operatingHours(operating)
                    .idleHours(idle)
                    .breakdownHours(breakdown)
                    .fuelConsumed(fuel)
                    .operatorName(operator)
                    .status(status)
                    .remarks(null)
                    .build();
                equipmentLogRepository.save(row);
                rows++;
            }
            eIdx++;
        }
        log.info("[IC-PMS Equipment Logs] seeded {} log rows across {} equipment × {} days",
            rows, equipment.size(), WINDOW_DAYS);
    }
}
