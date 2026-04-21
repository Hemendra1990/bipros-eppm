package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.contract.application.service.ContractKpiService;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;
import com.bipros.contract.domain.model.PerformanceBond;
import com.bipros.contract.domain.model.BondStatus;
import com.bipros.contract.domain.model.BondType;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IC-PMS Phase B seeder — DMIC contract register (M5).
 *
 * <p>Seeds 15 contracts spanning all 6 FIDIC/unit-rate variants, linked to WBS package codes,
 * with SPI/CPI/progress denormalised for MPR/dashboard rollups. After inserts, invokes
 * {@link ContractKpiService#refreshById(UUID)} so VO/BG expiry columns are fresh.
 *
 * <p>Sentinel: first DMIC-N03 contract exists → skip.
 */
@Slf4j
@Component
@Profile("dev")
@Order(102)
@RequiredArgsConstructor
public class IcpmsPhaseBSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final OrganisationRepository organisationRepository;
    private final ContractRepository contractRepository;
    private final PerformanceBondRepository performanceBondRepository;
    private final ContractKpiService contractKpiService;

    @Override
    @Transactional
    public void run(String... args) {
        // Sentinel widened so the Excel master-data loader takes precedence:
        // if any contract is already present (from Excel or a prior Phase B run), skip.
        if (contractRepository.count() > 0) {
            log.info("[IC-PMS Phase B] contracts already seeded, skipping");
            return;
        }

        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Phase B] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        log.info("[IC-PMS Phase B] seeding DMIC contract register…");

        seed(projectId, "LOA/DMIC/N03/P01/2024-01", "DMIC-N03-P01",
                "Dholera Trunk Infrastructure Package-1",
                "LNT-IDPL", ContractType.EPC_LUMP_SUM_FIDIC_YELLOW,
                new BigDecimal("2200.00"), LocalDate.of(2024, 4, 10),
                new BigDecimal("0.95"), new BigDecimal("0.98"), new BigDecimal("42.50"),
                ContractStatus.ACTIVE, LocalDate.of(2027, 5, 30));

        seed(projectId, "LOA/DMIC/N03/P02/2024-02", "DMIC-N03-P02",
                "Dholera Smart City Utilities Package-2",
                "TATA-PROJ", ContractType.EPC_LUMP_SUM_FIDIC_RED,
                new BigDecimal("1850.00"), LocalDate.of(2024, 5, 15),
                new BigDecimal("1.02"), new BigDecimal("1.00"), new BigDecimal("38.20"),
                ContractStatus.ACTIVE, LocalDate.of(2027, 6, 30));

        seed(projectId, "LOA/DMIC/N03/P03/2024-03", "DMIC-N03-P03",
                "Dholera Water Supply Package-3",
                "AFCONS", ContractType.ITEM_RATE_FIDIC_RED,
                new BigDecimal("950.00"), LocalDate.of(2024, 6, 1),
                new BigDecimal("0.88"), new BigDecimal("0.91"), new BigDecimal("28.50"),
                ContractStatus.ACTIVE_AT_RISK, LocalDate.of(2026, 12, 31));

        seed(projectId, "LOA/DMIC/N04/P01/2024-04", "DMIC-N04-P01",
                "Shendra-Bidkin Industrial Park Package-1",
                "HCC", ContractType.EPC_LUMP_SUM_FIDIC_SILVER,
                new BigDecimal("1650.00"), LocalDate.of(2024, 3, 20),
                new BigDecimal("0.82"), new BigDecimal("0.85"), new BigDecimal("55.30"),
                ContractStatus.ACTIVE_DELAYED, LocalDate.of(2026, 9, 30));

        seed(projectId, "LOA/DMIC/N04/P02/2024-05", "DMIC-N04-P02",
                "Shendra-Bidkin Roads Package-2",
                "DILIP-BUILDCON", ContractType.ITEM_RATE_FIDIC_RED,
                new BigDecimal("780.00"), LocalDate.of(2024, 4, 25),
                new BigDecimal("1.05"), new BigDecimal("1.03"), new BigDecimal("48.00"),
                ContractStatus.ACTIVE, LocalDate.of(2026, 11, 30));

        seed(projectId, "LOA/DMIC/N05/P01/2024-06", "DMIC-N05-P01",
                "Khushkhera-Bhiwadi-Neemrana Package-1",
                "LNT-IDPL", ContractType.EPC_LUMP_SUM_FIDIC_YELLOW,
                new BigDecimal("1400.00"), LocalDate.of(2024, 5, 10),
                new BigDecimal("0.92"), new BigDecimal("0.94"), new BigDecimal("35.80"),
                ContractStatus.ACTIVE, LocalDate.of(2027, 4, 30));

        seed(projectId, "LOA/DMIC/N05/P02/2024-07", "DMIC-N05-P02",
                "KBN Power Distribution Package-2",
                "AFCONS", ContractType.EPC_LUMP_SUM_FIDIC_RED,
                new BigDecimal("620.00"), LocalDate.of(2024, 7, 1),
                new BigDecimal("0.98"), new BigDecimal("1.01"), new BigDecimal("22.00"),
                ContractStatus.ACTIVE, LocalDate.of(2026, 10, 31));

        seed(projectId, "LOA/DMIC/N06/P01/2024-08", "DMIC-N06-P01",
                "Pithampur-Dhar-Mhow Infrastructure Package-1",
                "TATA-PROJ", ContractType.EPC_LUMP_SUM_FIDIC_YELLOW,
                new BigDecimal("1950.00"), LocalDate.of(2024, 2, 15),
                new BigDecimal("1.10"), new BigDecimal("1.08"), new BigDecimal("62.40"),
                ContractStatus.ACTIVE, LocalDate.of(2026, 8, 31));

        seed(projectId, "LOA/DMIC/N06/P02/2024-09", "DMIC-N06-P02",
                "PDM Industrial Utilities Package-2",
                "HCC", ContractType.EPC_LUMP_SUM_FIDIC_SILVER,
                new BigDecimal("980.00"), LocalDate.of(2024, 8, 1),
                new BigDecimal("0.75"), new BigDecimal("0.78"), new BigDecimal("15.20"),
                ContractStatus.MOBILISATION, LocalDate.of(2027, 7, 31));

        seed(projectId, "LOA/DMIC/N08/P01/2024-10", "DMIC-N08-P01",
                "Ponneri Industrial Node Package-1",
                "DILIP-BUILDCON", ContractType.ITEM_RATE_FIDIC_RED,
                new BigDecimal("1250.00"), LocalDate.of(2024, 9, 10),
                null, null, new BigDecimal("5.00"),
                ContractStatus.MOBILISATION, LocalDate.of(2027, 9, 30));

        // PMC contracts — percentage-based, no SPI/CPI in spec
        seed(projectId, "LOA/DMIC/N03/PMC/2024-11", "DMIC-N03",
                "Project Management Consultancy — Dholera",
                "AECOM-TYPSA", ContractType.PERCENTAGE_BASED_PMC,
                new BigDecimal("125.00"), LocalDate.of(2024, 1, 15),
                null, null, null,
                ContractStatus.ACTIVE, LocalDate.of(2028, 12, 31));

        seed(projectId, "LOA/DMIC/N04/PMC/2024-12", "DMIC-N04",
                "Project Management Consultancy — Shendra-Bidkin",
                "EGIS-PMC", ContractType.PERCENTAGE_BASED_PMC,
                new BigDecimal("95.00"), LocalDate.of(2024, 1, 20),
                null, null, null,
                ContractStatus.ACTIVE, LocalDate.of(2028, 12, 31));

        seed(projectId, "LOA/DMIC/N05/PMC/2024-13", "DMIC-N05",
                "Project Management Consultancy — KBN",
                "MOTT-MAC", ContractType.PERCENTAGE_BASED_PMC,
                new BigDecimal("85.00"), LocalDate.of(2024, 2, 1),
                null, null, null,
                ContractStatus.ACTIVE, LocalDate.of(2028, 12, 31));

        // Lump-sum / unit-rate design contract
        seed(projectId, "LOA/DMIC/N03/DSGN/2024-14", "DMIC-N03",
                "Detailed Design & Engineering — Dholera",
                "AECOM-TYPSA", ContractType.LUMP_SUM_UNIT_RATE,
                new BigDecimal("45.00"), LocalDate.of(2023, 10, 15),
                null, null, new BigDecimal("100.00"),
                ContractStatus.COMPLETED, null);

        seed(projectId, "LOA/DMIC/N08/DSGN/2024-15", "DMIC-N08",
                "Detailed Design & Engineering — Ponneri",
                "MOTT-MAC", ContractType.LUMP_SUM_UNIT_RATE,
                new BigDecimal("38.00"), LocalDate.of(2024, 3, 1),
                null, null, new BigDecimal("85.50"),
                ContractStatus.ACTIVE, LocalDate.of(2025, 12, 31));

        // Refresh denormalised KPI columns via the service
        int refreshed = 0;
        for (Contract c : contractRepository.findAll()) {
            try {
                contractKpiService.refreshById(c.getId());
                refreshed++;
            } catch (Exception e) {
                log.warn("[IC-PMS Phase B] KPI refresh failed for {}: {}", c.getContractNumber(), e.getMessage());
            }
        }

        log.info("[IC-PMS Phase B] seeded 15 DMIC contracts across 6 FIDIC variants; KPI refreshed for {} contracts", refreshed);
    }

    private void seed(UUID projectId,
                      String contractNumber,
                      String wbsPackageCode,
                      String packageDescription,
                      String contractorOrgCode,
                      ContractType type,
                      BigDecimal valueCrores,
                      LocalDate loaDate,
                      BigDecimal spi,
                      BigDecimal cpi,
                      BigDecimal progressAi,
                      ContractStatus status,
                      LocalDate bgExpiry) {

        Optional<Organisation> contractor = organisationRepository.findByCode(contractorOrgCode);
        if (contractor.isEmpty()) {
            log.warn("[IC-PMS Phase B] contractor {} missing — skipping {}", contractorOrgCode, contractNumber);
            return;
        }

        List<WbsNode> wbsMatches = wbsNodeRepository.findAll().stream()
                .filter(n -> wbsPackageCode.equals(n.getCode()))
                .toList();

        Contract c = new Contract();
        c.setProjectId(projectId);
        c.setContractNumber(contractNumber);
        c.setLoaNumber(contractNumber);
        c.setContractorName(contractor.get().getName());
        c.setContractorCode(contractor.get().getCode());
        c.setContractValue(valueCrores);
        c.setLoaDate(loaDate);
        c.setStartDate(loaDate.plusDays(21));
        c.setCompletionDate(loaDate.plusYears(3));
        c.setDlpMonths(24);
        c.setLdRate(0.05);
        c.setStatus(status);
        c.setContractType(type);
        c.setWbsPackageCode(wbsPackageCode);
        c.setPackageDescription(packageDescription);
        c.setSpi(spi);
        c.setCpi(cpi);
        c.setPhysicalProgressAi(progressAi);
        c.setCumulativeRaBillsCrores(progressAi == null
                ? BigDecimal.ZERO
                : valueCrores.multiply(progressAi).divide(new BigDecimal("100")));
        c.setPerformanceScore(spi == null ? new BigDecimal("75.00")
                : BigDecimal.valueOf(70 + spi.doubleValue() * 25));
        Contract saved = contractRepository.save(c);

        // Add one Performance Bond per contract so ContractKpiService picks up BG expiry
        if (bgExpiry != null) {
            PerformanceBond bond = new PerformanceBond();
            bond.setContractId(saved.getId());
            bond.setBondType(BondType.PERFORMANCE_GUARANTEE);
            bond.setBondValue(valueCrores.multiply(new BigDecimal("0.10")));
            bond.setBankName("State Bank of India");
            bond.setIssueDate(loaDate);
            bond.setExpiryDate(bgExpiry);
            bond.setStatus(BondStatus.ACTIVE);
            performanceBondRepository.save(bond);
        }
    }
}
