package com.bipros.api.config.seeder;

import com.bipros.cost.domain.entity.FundingSource;
import com.bipros.cost.domain.entity.ProjectFunding;
import com.bipros.cost.domain.repository.FundingSourceRepository;
import com.bipros.cost.domain.repository.ProjectFundingRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * IC-PMS M4 GAP — DMIC-PROG funding sources and allocation.
 *
 * <p>Seeds the three canonical DMIC corridor funding pillars — Central Govt equity,
 * State Govt equity, and the JICA loan tranche — with amounts denominated in rupees
 * (₹ crore × 10,000,000 → ₹). Then allocates each source to the DMIC-PROG programme
 * via {@link ProjectFunding}.
 *
 * <p>Field-name reality check:
 * <ul>
 *   <li>{@code FundingSource} fields: {@code name}, {@code description}, {@code code},
 *       {@code totalAmount}, {@code allocatedAmount}, {@code remainingAmount}. It does
 *       NOT have {@code sourceType} (GRANT/EQUITY/LOAN), {@code currency}, or
 *       {@code providerOrgId} — the provider identification and funding mechanism are
 *       described in free-text {@code description}. Amount stored in absolute ₹.</li>
 *   <li>{@code ProjectFunding} fields: {@code projectId}, {@code fundingSourceId},
 *       {@code wbsNodeId} (optional — we leave null for programme-wide allocation),
 *       {@code allocatedAmount}. No {@code drawnAmount} or {@code status} — drawdown
 *       and state are tracked on the FundingSource ({@code allocatedAmount} /
 *       {@code remainingAmount}).</li>
 * </ul>
 *
 * <p>Judgment calls:
 * <ul>
 *   <li>{@code totalAmount} on each FundingSource reflects the total commitment for the
 *       corridor; {@code allocatedAmount} reflects the share committed to DMIC-PROG
 *       (matches the single {@link ProjectFunding} row we create); {@code remainingAmount}
 *       is {@code totalAmount − allocatedAmount} — i.e. the headroom for other N-node
 *       projects in the corridor.</li>
 *   <li>Amounts: Central Equity ₹90,000 cr, State Equity ₹30,000 cr, JICA Loan
 *       ₹30,000 cr = ₹150,000 cr corridor envelope. DMIC-PROG (Dholera SIR) allocation
 *       uses ~15% of each source (₹13,500 cr / ₹4,500 cr / ₹4,500 cr = ₹22,500 cr total)
 *       to cover the N03 ₹22,000 cr budget with a small liquidity cushion.</li>
 * </ul>
 *
 * <p>Sentinel: {@code fundingSourceRepository.count() > 0} — skips on re-run.
 */
@Slf4j
@Component
@Profile("dev")
@Order(123)
@RequiredArgsConstructor
public class IcpmsFundingSeeder implements CommandLineRunner {

    private final FundingSourceRepository fundingSourceRepository;
    private final ProjectFundingRepository projectFundingRepository;
    private final ProjectRepository projectRepository;

    private static final BigDecimal CRORE = new BigDecimal("10000000"); // 1 cr = ₹1,00,00,000

    @Override
    @Transactional
    public void run(String... args) {
        if (fundingSourceRepository.count() > 0) {
            log.info("[IC-PMS M4 GAP] funding sources already present, skipping");
            return;
        }

        Project project = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (project == null) {
            log.warn("[IC-PMS M2 GAP] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = project.getId();

        // ---- sources ----
        FundingSource central = saveSource(
                "FS-DMIC-CENTRAL-EQ",
                "Central Government Equity — DMIC Trust Fund",
                "Grant-style equity infused by the Government of India through the DMIC Trust Fund, routed via "
                        + "NICDC (National Industrial Corridor Development Corp). Type: GRANT. Currency: INR.",
                new BigDecimal("90000").multiply(CRORE),  // ₹90,000 cr total envelope
                new BigDecimal("13500").multiply(CRORE)); // ₹13,500 cr allocated to DMIC-PROG

        FundingSource state = saveSource(
                "FS-DMIC-STATE-EQ",
                "State Government Equity — Gujarat Contribution",
                "State equity contribution from the Government of Gujarat, administered via DMICDC "
                        + "(Delhi Mumbai Industrial Corridor Development Corp). Type: EQUITY. Currency: INR.",
                new BigDecimal("30000").multiply(CRORE),  // ₹30,000 cr total envelope
                new BigDecimal("4500").multiply(CRORE));  // ₹4,500 cr allocated to DMIC-PROG

        FundingSource jica = saveSource(
                "FS-DMIC-JICA-LOAN",
                "JICA ODA Loan — DMIC Corridor Facility",
                "Japan International Cooperation Agency (JICA) Official Development Assistance soft loan, "
                        + "on-lent to the corridor programme through NICDC. Type: LOAN. Currency: JPY hedged to INR.",
                new BigDecimal("30000").multiply(CRORE),  // ₹30,000 cr total envelope
                new BigDecimal("4500").multiply(CRORE));  // ₹4,500 cr allocated to DMIC-PROG

        // ---- project allocations ----
        saveAllocation(projectId, central.getId(), new BigDecimal("13500").multiply(CRORE));
        saveAllocation(projectId, state.getId(), new BigDecimal("4500").multiply(CRORE));
        saveAllocation(projectId, jica.getId(), new BigDecimal("4500").multiply(CRORE));

        log.info("[IC-PMS M4 GAP] seeded 3 funding sources (₹1,50,000 cr envelope) and 3 project-funding rows "
                + "allocating ₹22,500 cr to DMIC-PROG");
    }

    // ------------------------------------------------------------------ helpers

    private FundingSource saveSource(String code, String name, String description,
                                     BigDecimal total, BigDecimal allocated) {
        FundingSource s = new FundingSource();
        s.setCode(code);
        s.setName(name);
        s.setDescription(description);
        s.setTotalAmount(total);
        s.setAllocatedAmount(allocated);
        s.setRemainingAmount(total.subtract(allocated));
        return fundingSourceRepository.save(s);
    }

    private void saveAllocation(UUID projectId, UUID fundingSourceId, BigDecimal amount) {
        ProjectFunding pf = new ProjectFunding();
        pf.setProjectId(projectId);
        pf.setFundingSourceId(fundingSourceId);
        pf.setAllocatedAmount(amount);
        // wbsNodeId left null — allocation is programme-wide, not pinned to a package.
        projectFundingRepository.save(pf);
    }
}
