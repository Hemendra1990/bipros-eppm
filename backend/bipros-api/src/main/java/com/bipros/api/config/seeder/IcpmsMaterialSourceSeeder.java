package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.LabTestStatus;
import com.bipros.resource.domain.model.MaterialSource;
import com.bipros.resource.domain.model.MaterialSourceLabTest;
import com.bipros.resource.domain.model.MaterialSourceType;
import com.bipros.resource.domain.repository.MaterialSourceLabTestRepository;
import com.bipros.resource.domain.repository.MaterialSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds PMS MasterData Screen 08: two borrow areas + one quarry + one bitumen depot for the
 * NHAI NH-48 project, with All-Pass lab test status.
 */
@Slf4j
@Component
@Profile("dev")
@Order(961)
@RequiredArgsConstructor
public class IcpmsMaterialSourceSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final MaterialSourceRepository sourceRepository;
    private final MaterialSourceLabTestRepository labTestRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Seed 4 reference sources (2 borrow areas + 1 quarry + 1 bitumen depot) per project
        // that has corridor chainage info. Idempotent per project via the (project, source_code)
        // unique constraint — existing projects are skipped individually.
        int seededProjects = 0;
        for (Project p : projectRepository.findAll()) {
            if (p.getFromChainageM() == null) continue;
            if (!sourceRepository.findByProjectId(p.getId()).isEmpty()) continue;
            seedBorrowArea(p, "BA-001", "Khodan Village", 8.5,
                new BigDecimal("18.50"), new BigDecimal("2.045"));
            seedBorrowArea(p, "BA-002", "Ratangarh Village", 12.3,
                new BigDecimal("22.10"), new BigDecimal("2.112"));
            seedQuarry(p, "QRY-001", "Ajmer Granite Quarry", 45.2);
            seedBitumenDepot(p, "BD-001", "IOCL Ajmer Bulk Terminal", 48.0);
            seededProjects++;
            log.info("[IC-PMS MaterialSource] seeded 4 sources for project {}", p.getCode());
        }
        if (seededProjects == 0) {
            log.info("[IC-PMS MaterialSource] all eligible projects already seeded, skipping");
        }
    }

    private void seedBorrowArea(Project p, String code, String village, double distanceKm,
                                 BigDecimal cbr, BigDecimal mdd) {
        MaterialSource s = sourceRepository.save(MaterialSource.builder()
            .projectId(p.getId())
            .sourceCode(code)
            .name(village + " Borrow Area")
            .sourceType(MaterialSourceType.BORROW_AREA)
            .village(village)
            .district("Ajmer")
            .state("Rajasthan")
            .distanceKm(new BigDecimal(String.valueOf(distanceKm)))
            .approvedQuantity(new BigDecimal("150000"))
            .approvedQuantityUnit("CU_M")
            .approvalReference("REV/NHAI/" + code + "/2025")
            .approvalAuthority("Revenue Dept. Ajmer")
            .cbrAveragePercent(cbr)
            .mddGcc(mdd)
            .labTestStatus(LabTestStatus.ALL_PASS)
            .build());
        recordTest(s.getId(), "CBR", "IS 2720 Pt.16", cbr, "%");
        recordTest(s.getId(), "MDD", "IS 2720 Pt.7", mdd, "g/cc");
        recordTest(s.getId(), "Liquid Limit", "IS 2720 Pt.5", new BigDecimal("25.4"), "%");
    }

    private void seedQuarry(Project p, String code, String name, double distanceKm) {
        MaterialSource s = sourceRepository.save(MaterialSource.builder()
            .projectId(p.getId())
            .sourceCode(code)
            .name(name)
            .sourceType(MaterialSourceType.QUARRY)
            .village(name)
            .district("Ajmer")
            .state("Rajasthan")
            .distanceKm(new BigDecimal(String.valueOf(distanceKm)))
            .approvedQuantity(new BigDecimal("85000"))
            .approvedQuantityUnit("MT")
            .approvalReference("MINES/RJ/" + code + "/2025")
            .approvalAuthority("State Mining Dept.")
            .labTestStatus(LabTestStatus.ALL_PASS)
            .build());
        recordTest(s.getId(), "Aggregate Impact Value", "IS 2386 Pt.4",
            new BigDecimal("18.5"), "%");
        recordTest(s.getId(), "Los Angeles Abrasion", "IS 2386 Pt.4",
            new BigDecimal("22.0"), "%");
    }

    private void seedBitumenDepot(Project p, String code, String name, double distanceKm) {
        sourceRepository.save(MaterialSource.builder()
            .projectId(p.getId())
            .sourceCode(code)
            .name(name)
            .sourceType(MaterialSourceType.BITUMEN_DEPOT)
            .village(name)
            .district("Ajmer")
            .state("Rajasthan")
            .distanceKm(new BigDecimal(String.valueOf(distanceKm)))
            .approvedQuantity(new BigDecimal("2500"))
            .approvedQuantityUnit("MT")
            .approvalReference("IOCL/Supply/2025-26/001")
            .approvalAuthority("IOCL Commercial")
            .labTestStatus(LabTestStatus.ALL_PASS)
            .build());
    }

    private void recordTest(java.util.UUID sourceId, String testName,
                            String standardRef, BigDecimal value, String unit) {
        labTestRepository.save(MaterialSourceLabTest.builder()
            .sourceId(sourceId)
            .testName(testName)
            .standardReference(standardRef)
            .resultValue(value)
            .resultUnit(unit)
            .passed(true)
            .testDate(LocalDate.now().minusMonths(2))
            .build());
    }
}
