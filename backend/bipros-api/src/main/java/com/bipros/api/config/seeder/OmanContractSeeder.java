package com.bipros.api.config.seeder;

import com.bipros.contract.domain.model.BidSubmission;
import com.bipros.contract.domain.model.BidSubmissionStatus;
import com.bipros.contract.domain.model.BillingCycle;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractMilestone;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;
import com.bipros.contract.domain.model.ContractorScorecard;
import com.bipros.contract.domain.model.MilestoneStatus;
import com.bipros.contract.domain.model.ProcurementMethod;
import com.bipros.contract.domain.model.ProcurementPlan;
import com.bipros.contract.domain.model.ProcurementPlanStatus;
import com.bipros.contract.domain.model.Tender;
import com.bipros.contract.domain.model.TenderStatus;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.model.VariationOrderStatus;
import com.bipros.contract.domain.repository.BidSubmissionRepository;
import com.bipros.contract.domain.repository.ContractMilestoneRepository;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.ContractorScorecardRepository;
import com.bipros.contract.domain.repository.ProcurementPlanRepository;
import com.bipros.contract.domain.repository.TenderRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(144)
@RequiredArgsConstructor
public class OmanContractSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final ContractMilestoneRepository contractMilestoneRepository;
    private final ContractorScorecardRepository contractorScorecardRepository;
    private final VariationOrderRepository variationOrderRepository;
    private final ProcurementPlanRepository procurementPlanRepository;
    private final TenderRepository tenderRepository;
    private final BidSubmissionRepository bidSubmissionRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-CONTRACT] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (!contractRepository.findByProjectId(projectId).isEmpty()) {
            log.info("[BNK-CONTRACT] contracts already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        log.info("[BNK-CONTRACT] seeding contract data for project '{}'", PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        Contract contract = seedContract(projectId);
        seedContractMilestones(contract.getId());
        seedContractorScorecards(contract.getId(), rng);
        seedVariationOrders(contract.getId(), rng);
        seedBidSubmissions(projectId, rng);

        log.info("[BNK-CONTRACT] contract seeding completed");
    }

    private Contract seedContract(UUID projectId) {
        Contract c = new Contract();
        c.setProjectId(projectId);
        c.setContractNumber("BNK-2024-6155");
        c.setContractorName("BNK Joint Venture — Barka Nakhal");
        c.setContractorCode("BNK-JV");
        c.setContractValue(new BigDecimal("75000000.00"));
        c.setRevisedValue(new BigDecimal("75000000.00"));
        c.setLoaNumber("LOA-MOT-2024-BNK-6155");
        c.setLoaDate(LocalDate.of(2024, 8, 15));
        c.setNtpDate(LocalDate.of(2024, 9, 1));
        c.setStartDate(LocalDate.of(2024, 9, 1));
        c.setCompletionDate(LocalDate.of(2026, 8, 31));
        c.setDlpMonths(12);
        c.setLdRate(0.1);
        c.setStatus(ContractStatus.ACTIVE);
        c.setContractType(ContractType.EPC);
        c.setDescription("Design and construction of Barka–Nakhal dual carriageway (41 km), including wadi bridge, "
                + "pavement, drainage, road furniture, and street lighting.");
        c.setCurrency("OMR");
        c.setRetentionPct(new BigDecimal("5.00"));
        c.setMobilisationAdvancePct(new BigDecimal("10.00"));
        c.setPerformanceBgPct(new BigDecimal("5.00"));
        c.setPaymentTermsDays(30);
        c.setBillingCycle(BillingCycle.MONTHLY);
        c.setWbsPackageCode("BNK-6155");
        c.setPackageDescription("Barka–Nakhal Road EPC Contract");
        c.setPerformanceScore(new BigDecimal("78.50"));
        return contractRepository.save(c);
    }

    private void seedContractMilestones(UUID contractId) {
        record MilestoneSpec(String code, String name, LocalDate target, LocalDate actual,
                             MilestoneStatus status, Double pct, BigDecimal amount) {}
        List<MilestoneSpec> milestones = List.of(
                new MilestoneSpec("MS-01", "Mobilisation Complete",
                        LocalDate.of(2024, 10, 15), LocalDate.of(2024, 10, 12),
                        MilestoneStatus.ACHIEVED, 10.0, new BigDecimal("7500000.00")),
                new MilestoneSpec("MS-02", "Earthworks 50%",
                        LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 8),
                        MilestoneStatus.ACHIEVED, 20.0, new BigDecimal("15000000.00")),
                new MilestoneSpec("MS-03", "Pavement Start",
                        LocalDate.of(2025, 9, 30), null,
                        MilestoneStatus.PENDING, 30.0, new BigDecimal("22500000.00")),
                new MilestoneSpec("MS-04", "Substantial Completion",
                        LocalDate.of(2026, 8, 31), null,
                        MilestoneStatus.PENDING, 40.0, new BigDecimal("30000000.00"))
        );
        for (MilestoneSpec ms : milestones) {
            ContractMilestone m = new ContractMilestone();
            m.setContractId(contractId);
            m.setMilestoneCode(ms.code());
            m.setMilestoneName(ms.name());
            m.setTargetDate(ms.target());
            m.setActualDate(ms.actual());
            m.setStatus(ms.status());
            m.setPaymentPercentage(ms.pct());
            m.setAmount(ms.amount());
            contractMilestoneRepository.save(m);
        }
        log.info("[BNK-CONTRACT] seeded {} contract milestones", milestones.size());
    }

    private void seedContractorScorecards(UUID contractId, Random rng) {
        String[] periods = {"Q1-2025", "Q2-2025", "Q3-2025", "Q4-2025"};
        for (String period : periods) {
            double quality = 72 + rng.nextDouble() * 18;
            double safety = 75 + rng.nextDouble() * 15;
            double progress = 68 + rng.nextDouble() * 22;
            double payment = 80 + rng.nextDouble() * 15;
            double overall = (quality + safety + progress + payment) / 4.0;

            ContractorScorecard sc = new ContractorScorecard();
            sc.setContractId(contractId);
            sc.setPeriod(period);
            sc.setQualityScore(round2(quality));
            sc.setSafetyScore(round2(safety));
            sc.setProgressScore(round2(progress));
            sc.setPaymentComplianceScore(round2(payment));
            sc.setOverallScore(round2(overall));
            sc.setRemarks(period + " assessment — " + (overall >= 80 ? "Satisfactory" : "Needs improvement"));
            contractorScorecardRepository.save(sc);
        }
        log.info("[BNK-CONTRACT] seeded 4 contractor scorecards");
    }

    private void seedVariationOrders(UUID contractId, Random rng) {
        record VoSpec(String number, String desc, BigDecimal value, String justification,
                      VariationOrderStatus status, BigDecimal budgetImpact, Integer scheduleImpact) {}
        List<VoSpec> vos = List.of(
                new VoSpec("VO-001", "Wadi crossing foundation redesign — soft strata at Ch 18+200 required deeper piles",
                        new BigDecimal("1850000.00"), "Unforeseen ground conditions — CBR retest confirmed weak substrate",
                        VariationOrderStatus.APPROVED, new BigDecimal("1850000.00"), 18),
                new VoSpec("VO-002", "High-mast lighting at urban segment Ch 32–37 per RFI-BNK-009",
                        new BigDecimal("620000.00"), "Client scope change — enhanced lighting per safety audit",
                        VariationOrderStatus.RECOMMENDED, new BigDecimal("620000.00"), 7),
                new VoSpec("VO-003", "Traffic diversion at Barka junction — temporary signalisation",
                        new BigDecimal("340000.00"), "Traffic management plan revision — ROP requirement",
                        VariationOrderStatus.INITIATED, new BigDecimal("340000.00"), 5)
        );
        for (VoSpec vo : vos) {
            VariationOrder v = new VariationOrder();
            v.setContractId(contractId);
            v.setVoNumber(vo.number());
            v.setDescription(vo.desc());
            v.setVoValue(vo.value());
            v.setJustification(vo.justification());
            v.setStatus(vo.status());
            v.setImpactOnBudget(vo.budgetImpact());
            v.setImpactOnScheduleDays(vo.scheduleImpact());
            if (vo.status() == VariationOrderStatus.APPROVED) {
                v.setApprovedBy("MoTC — Director of Roads");
                v.setApprovedAt(Instant.parse("2025-02-10T10:00:00Z"));
            }
            variationOrderRepository.save(v);
        }
        log.info("[BNK-CONTRACT] seeded {} variation orders", vos.size());
    }

    private void seedBidSubmissions(UUID projectId, Random rng) {
        ProcurementPlan plan = new ProcurementPlan();
        plan.setProjectId(projectId);
        plan.setPlanCode("BNK-PROC-6155");
        plan.setDescription("Procurement plan for Barka–Nakhal Road EPC contract");
        plan.setProcurementMethod(ProcurementMethod.OPEN_TENDER);
        plan.setEstimatedValue(new BigDecimal("75000000.00"));
        plan.setCurrency("OMR");
        plan.setTargetNitDate(LocalDate.of(2024, 5, 1));
        plan.setTargetAwardDate(LocalDate.of(2024, 8, 1));
        plan.setStatus(ProcurementPlanStatus.COMPLETED);
        plan.setApprovedBy("MoTC Tender Board");
        plan.setApprovedAt(Instant.parse("2024-05-10T00:00:00Z"));
        ProcurementPlan savedPlan = procurementPlanRepository.save(plan);

        Tender tender = new Tender();
        tender.setProcurementPlanId(savedPlan.getId());
        tender.setProjectId(projectId);
        tender.setTenderNumber("TENDER-MOT-2024-BNK-6155");
        tender.setNitDate(LocalDate.of(2024, 5, 15));
        tender.setScope("EPC — Barka to Nakhal dual carriageway, 41 km, including bridge, pavement, drainage, lighting");
        tender.setEstimatedValue(new BigDecimal("75000000.00"));
        tender.setEmdAmount(new BigDecimal("750000.00"));
        tender.setCompletionPeriodDays(720);
        tender.setBidDueDate(LocalDate.of(2024, 7, 15));
        tender.setBidOpenDate(LocalDate.of(2024, 7, 16));
        tender.setStatus(TenderStatus.AWARDED);
        Tender savedTender = tenderRepository.save(tender);

        record BidSpec(String name, String code, Double techScore, BigDecimal financial, BidSubmissionStatus status, String remarks) {}
        List<BidSpec> bids = List.of(
                new BidSpec("BNK Joint Venture — Barka Nakhal", "BNK-JV", 87.5, new BigDecimal("74800000.00"),
                        BidSubmissionStatus.AWARDED, "L1 — combined technical + financial evaluation"),
                new BidSpec("Galfar Engineering & Contracting", "GALFAR", 82.0, new BigDecimal("76200000.00"),
                        BidSubmissionStatus.TECHNICALLY_QUALIFIED, "L2 — strong technical but higher financial bid"),
                new BidSpec("Consolidated Contractors Company (CCC)", "CCC", 79.5, new BigDecimal("77500000.00"),
                        BidSubmissionStatus.TECHNICALLY_QUALIFIED, "L3 — qualified but premium pricing"),
                new BidSpec("Strabag Oman LLC", "STRABAG", 68.0, new BigDecimal("72000000.00"),
                        BidSubmissionStatus.NOT_QUALIFIED, "Failed technical — insufficient local experience"),
                new BidSpec("Nawaz Engineering LLC", "NAWAZ", 55.0, new BigDecimal("68000000.00"),
                        BidSubmissionStatus.REJECTED, "Non-responsive — missing EMD and performance BG")
        );
        for (BidSpec b : bids) {
            BidSubmission bs = new BidSubmission();
            bs.setTenderId(savedTender.getId());
            bs.setBidderName(b.name());
            bs.setBidderCode(b.code());
            bs.setTechnicalScore(b.techScore());
            bs.setFinancialBid(b.financial());
            bs.setStatus(b.status());
            bs.setEvaluationRemarks(b.remarks());
            bidSubmissionRepository.save(bs);
        }
        savedTender.setAwardedContractId(contractRepository.findByContractNumber("BNK-2024-6155")
                .map(Contract::getId).orElse(null));
        tenderRepository.save(savedTender);

        log.info("[BNK-CONTRACT] seeded procurement plan, tender, and {} bid submissions", bids.size());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
