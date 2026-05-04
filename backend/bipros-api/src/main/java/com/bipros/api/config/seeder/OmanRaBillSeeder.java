package com.bipros.api.config.seeder;

import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.RaBillItem;
import com.bipros.cost.domain.entity.SatelliteGate;
import com.bipros.cost.domain.repository.RaBillItemRepository;
import com.bipros.cost.domain.repository.RaBillRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(145)
@RequiredArgsConstructor
public class OmanRaBillSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private static final String BILL_PREFIX = "BNK-RA";

    private static final List<BoqItem> BOQ_ITEMS = List.of(
            new BoqItem("BOQ-001", "Earthwork — Excavation", "Cum", new BigDecimal("3.50")),
            new BoqItem("BOQ-002", "Embankment — Borrow fill", "Cum", new BigDecimal("5.20")),
            new BoqItem("BOQ-003", "GSB Sub-base (150 mm)", "Cum", new BigDecimal("12.00")),
            new BoqItem("BOQ-004", "WMM Base (200 mm)", "Cum", new BigDecimal("15.50")),
            new BoqItem("BOQ-005", "DBM Binder (60 mm)", "Cum", new BigDecimal("28.00")),
            new BoqItem("BOQ-006", "BC Surface (40 mm)", "Cum", new BigDecimal("32.00")),
            new BoqItem("BOQ-007", "Reinforced Concrete C30", "Cum", new BigDecimal("45.00")),
            new BoqItem("BOQ-008", "Structural Steel", "MT", new BigDecimal("850.00")),
            new BoqItem("BOQ-009", "Drainage — RCC Pipe NP3 (1200 mm)", "Rmt", new BigDecimal("65.00")),
            new BoqItem("BOQ-010", "Road Marking — Thermoplastic", "Lmt", new BigDecimal("4.50"))
    );

    private final ProjectRepository projectRepository;
    private final RaBillRepository raBillRepository;
    private final RaBillItemRepository raBillItemRepository;

    @Override
    public void run(String... args) {
        Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
        if (project == null) {
            log.warn("[BNK-RABILL] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        UUID projectId = project.getId();

        if (!raBillRepository.findByProjectIdOrderByBillNumberDesc(projectId).isEmpty()) {
            log.info("[BNK-RABILL] RA bills already present for project '{}' — skipping", PROJECT_CODE);
            return;
        }

        log.info("[BNK-RABILL] seeding 12 monthly RA bills for project '{}'", PROJECT_CODE);
        Random rng = new Random(DETERMINISTIC_SEED);

        BigDecimal cumulative = BigDecimal.ZERO;
        BigDecimal totalContract = new BigDecimal("75000000.00");

        for (int i = 0; i < 12; i++) {
            int year = 2025 + (i / 12);
            int month = 4 + (i % 12);
            if (month > 12) {
                month -= 12;
                year++;
            }
            LocalDate periodFrom = LocalDate.of(year, month, 1);
            LocalDate periodTo = periodFrom.withDayOfMonth(periodFrom.lengthOfMonth());

            String billNumber = String.format("%s-%d-%03d", BILL_PREFIX, year, i + 1);

            BigDecimal grossAmount = computeGrossAmount(i, totalContract, rng);
            cumulative = cumulative.add(grossAmount);

            BigDecimal retention = grossAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tds = grossAmount.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netForGst = grossAmount.subtract(retention).subtract(tds);
            BigDecimal gst = netForGst.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalDeductions = retention.add(tds).add(gst);
            BigDecimal netAmount = grossAmount.subtract(totalDeductions);

            RaBill.RaBillStatus status;
            if (i < 8) {
                status = RaBill.RaBillStatus.PAID;
            } else if (i < 10) {
                status = RaBill.RaBillStatus.CERTIFIED;
            } else {
                status = RaBill.RaBillStatus.APPROVED;
            }

            double claimedPct = Math.min(100.0, 5.0 + i * 7.5 + rng.nextDouble() * 3.0);
            double aiPct = claimedPct - 1.0 - rng.nextDouble() * 3.0;
            double variance = Math.abs(claimedPct - aiPct);
            SatelliteGate gate = variance <= 5.0 ? SatelliteGate.PASS
                    : (variance <= 10.0 ? SatelliteGate.HOLD_VARIANCE : SatelliteGate.RED_VARIANCE);

            RaBill bill = new RaBill();
            bill.setProjectId(projectId);
            bill.setBillNumber(billNumber);
            bill.setBillPeriodFrom(periodFrom);
            bill.setBillPeriodTo(periodTo);
            bill.setGrossAmount(grossAmount.setScale(2, RoundingMode.HALF_UP));
            bill.setDeductions(totalDeductions.setScale(2, RoundingMode.HALF_UP));
            bill.setRetention5Pct(retention);
            bill.setTds2Pct(tds);
            bill.setGst18Pct(gst);
            bill.setMobAdvanceRecovery(i < 6 ? grossAmount.multiply(new BigDecimal("0.0167"))
                    .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            bill.setNetAmount(netAmount.setScale(2, RoundingMode.HALF_UP));
            bill.setCumulativeAmount(cumulative.setScale(2, RoundingMode.HALF_UP));
            bill.setContractorClaimedPercent(round2(claimedPct));
            bill.setAiSatellitePercent(round2(aiPct));
            bill.setSatelliteGate(gate);
            bill.setSatelliteGateVariance(round2(variance));
            bill.setStatus(status);
            bill.setSubmittedDate(periodTo.plusDays(5));
            if (status.ordinal() >= RaBill.RaBillStatus.CERTIFIED.ordinal()) {
                bill.setCertifiedDate(periodTo.plusDays(12));
                bill.setCertifiedBy("PMC — QS Lead");
            }
            if (status.ordinal() >= RaBill.RaBillStatus.APPROVED.ordinal()) {
                bill.setApprovedDate(periodTo.plusDays(18));
                bill.setApprovedBy("MoTC — Director of Roads");
            }
            if (status == RaBill.RaBillStatus.PAID) {
                bill.setPaidDate(periodTo.plusDays(28));
                bill.setPaymentDate(periodTo.plusDays(28));
                bill.setPfmsDpaRef("PFMS-DPA-" + billNumber);
            }
            bill.setRemarks("Monthly RA bill for " + periodFrom.getMonth() + " " + year);
            RaBill savedBill = raBillRepository.save(bill);

            seedRaBillItems(savedBill.getId(), i, rng);
        }

        log.info("[BNK-RABILL] seeded 12 monthly RA bills with items");
    }

    private void seedRaBillItems(UUID raBillId, int billIndex, Random rng) {
        int itemCount = 3 + (billIndex % 3);
        for (int j = 0; j < itemCount; j++) {
            BoqItem boq = BOQ_ITEMS.get((billIndex + j) % BOQ_ITEMS.size());
            double prevQty = billIndex > 0 ? (billIndex * 100.0 + rng.nextInt(50)) : 0.0;
            double currentQty = 80.0 + rng.nextInt(60);
            double cumulativeQty = prevQty + currentQty;
            BigDecimal amount = boq.rate().multiply(BigDecimal.valueOf(currentQty))
                    .setScale(2, RoundingMode.HALF_UP);

            RaBillItem item = new RaBillItem();
            item.setRaBillId(raBillId);
            item.setItemCode(boq.code());
            item.setDescription(boq.description());
            item.setUnit(boq.unit());
            item.setRate(boq.rate());
            item.setPreviousQuantity(round1(prevQty));
            item.setCurrentQuantity(round1(currentQty));
            item.setCumulativeQuantity(round1(cumulativeQty));
            item.setAmount(amount);
            raBillItemRepository.save(item);
        }
    }

    private BigDecimal computeGrossAmount(int billIndex, BigDecimal totalContract, Random rng) {
        BigDecimal basePct = new BigDecimal("0.06");
        double variation = 0.85 + rng.nextDouble() * 0.30;
        BigDecimal amount = totalContract.multiply(basePct)
                .multiply(BigDecimal.valueOf(variation))
                .setScale(2, RoundingMode.HALF_UP);
        if (billIndex >= 10) {
            amount = amount.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);
        }
        return amount;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(Math.round(v * 100.0) / 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    private record BoqItem(String code, String description, String unit, BigDecimal rate) {}
}
