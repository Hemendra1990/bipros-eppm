package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.LabourReturn;
import com.bipros.resource.domain.model.SkillCategory;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * IC-PMS M3 — seeds 14 days of daily labour returns for 5 EPC contractors
 * × 3 skill categories (Skilled / Semi-skilled / Unskilled) = ~210 rows.
 *
 * <p>Headcounts are tuned per contractor × skill within 20–150. Man-days equals
 * headcount for single-day returns. Contractor lookup is by code
 * (LNT-IDPL, TATA-PROJ, AFCONS, HCC, DILIP-BUILDCON) via {@link OrganisationRepository}.
 * Idempotent: skipped entirely if any labour returns already exist.
 */
@Slf4j
@Component
@Profile("dev")
@Order(111)
@RequiredArgsConstructor
public class IcpmsLabourReturnSeeder implements CommandLineRunner {

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 4, 1);
    private static final int WINDOW_DAYS = 14;

    private final LabourReturnRepository labourReturnRepository;
    private final OrganisationRepository organisationRepository;
    private final ProjectRepository projectRepository;

    /** Fixed per-contractor × per-skill baseline headcount (within 20–150). */
    private record ContractorMix(String code, String site,
                                 int skilled, int semiSkilled, int unskilled) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (labourReturnRepository.count() > 0) {
            log.info("[IC-PMS Labour Returns] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Labour Returns] DMIC-PROG project not found — run Phase A first");
            return;
        }

        List<ContractorMix> mixes = List.of(
            new ContractorMix("LNT-IDPL",      "Dholera Zone A",    60, 90, 140),
            new ContractorMix("TATA-PROJ",     "Shendra MIDC",      45, 70, 110),
            new ContractorMix("AFCONS",        "Dighi Port Zone 1", 40, 65, 100),
            new ContractorMix("HCC",           "Vikram Udyogpuri",  30, 55,  85),
            new ContractorMix("DILIP-BUILDCON","Greater Noida TOD", 25, 45,  70)
        );

        List<Organisation> resolved = new ArrayList<>();
        for (ContractorMix mix : mixes) {
            Organisation org = organisationRepository.findByCode(mix.code).orElse(null);
            if (org == null) {
                log.warn("[IC-PMS Labour Returns] contractor {} not found — skipping", mix.code);
            } else {
                resolved.add(org);
            }
        }
        if (resolved.isEmpty()) {
            log.warn("[IC-PMS Labour Returns] no EPC contractors found — run Phase A first");
            return;
        }

        int rows = 0;
        for (int i = 0; i < mixes.size(); i++) {
            ContractorMix mix = mixes.get(i);
            Organisation org = organisationRepository.findByCode(mix.code).orElse(null);
            if (org == null) continue;
            String contractorName = org.getShortName() != null ? org.getShortName() : org.getName();

            for (int d = 0; d < WINDOW_DAYS; d++) {
                LocalDate returnDate = WINDOW_START.plusDays(d);
                // Slight daily variation: ±5 headcount swing via (d % 3) - 1
                int swing = (d % 3) - 1;
                rows += save(programme, contractorName, returnDate,
                    SkillCategory.SKILLED,      mix.skilled + swing,      mix.site);
                rows += save(programme, contractorName, returnDate,
                    SkillCategory.SEMI_SKILLED, mix.semiSkilled + swing * 2, mix.site);
                rows += save(programme, contractorName, returnDate,
                    SkillCategory.UNSKILLED,    mix.unskilled + swing * 3,   mix.site);
            }
        }

        log.info("[IC-PMS Labour Returns] seeded {} rows ({} contractors × 3 skills × {} days)",
            rows, resolved.size(), WINDOW_DAYS);
    }

    private int save(Project programme, String contractorName, LocalDate returnDate,
                     SkillCategory skill, int headcount, String site) {
        int head = Math.max(20, headcount);  // clamp to minimum 20
        LabourReturn row = LabourReturn.builder()
            .projectId(programme.getId())
            .contractorName(contractorName)
            .returnDate(returnDate)
            .skillCategory(skill)
            .headCount(head)
            .manDays((double) head)
            .siteLocation(site)
            .remarks(null)
            .build();
        labourReturnRepository.save(row);
        return 1;
    }
}
