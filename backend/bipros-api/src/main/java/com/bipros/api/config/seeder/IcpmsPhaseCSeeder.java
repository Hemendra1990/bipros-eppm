package com.bipros.api.config.seeder;

import com.bipros.cost.application.service.SatelliteGateService;
import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.SatelliteGate;
import com.bipros.cost.domain.repository.RaBillRepository;
import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import com.bipros.gis.domain.model.SatelliteAlertFlag;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.SatelliteImageSource;
import com.bipros.gis.domain.model.SatelliteImageStatus;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * IC-PMS Phase C seeder — M3 satellite monitoring + M4 RA bills with satellite gate.
 *
 * <p>Seeds 25 satellite scenes (with CVI/EDI/NDVI/alertFlag) across 8 WBS packages and
 * 16 RA bills with contractor-claim / AI-satellite deltas. After each bill insert the
 * {@link SatelliteGateService} stamps {@code satelliteGate}. Seed values are tuned so
 * {@code DMIC-N03-P01-RA-006} lands on {@link SatelliteGate#HOLD_VARIANCE} with variance 7.00.
 *
 * <p>Sentinel: first {@code DMIC-N03-P01-RA-001} bill present → skip.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(103)
@RequiredArgsConstructor
public class IcpmsPhaseCSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final SatelliteImageRepository satelliteImageRepository;
    private final ConstructionProgressSnapshotRepository snapshotRepository;
    private final RaBillRepository raBillRepository;
    private final SatelliteGateService satelliteGateService;

    @Override
    @Transactional
    public void run(String... args) {
        // Sentinel widened so the Excel master-data loader takes precedence:
        // if any RA bill or satellite scene exists already, skip (loader owns both tables).
        if (raBillRepository.count() > 0 || satelliteImageRepository.count() > 0) {
            log.info("[IC-PMS Phase C] satellite/RA-bill data already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Phase C] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        log.info("[IC-PMS Phase C] seeding satellite scenes + RA bills…");

        // --- Satellite scenes (25 scenes across 8 packages) -------------------------
        // Columns: sceneId, captureDate, packageCode, aiProgress, cvi, edi, ndviChange, cloudCover, alert, source
        seedScene(projectId, "SCN-N03-250115", LocalDate.of(2025, 1, 15), "DMIC-N03-P01", 38.50, 72.0, 65.0, -0.02, 8.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N03-250215", LocalDate.of(2025, 2, 15), "DMIC-N03-P01", 40.20, 74.0, 67.0, -0.03, 5.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N03-250315", LocalDate.of(2025, 3, 15), "DMIC-N03-P01", 41.30, 76.0, 68.0, -0.04, 3.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.PLANET_LABS);
        // Tuned so DMIC-N03-P01-RA-006 (claim 49.50) hits variance 7 → HOLD_VARIANCE
        seedScene(projectId, "SCN-N03-250415", LocalDate.of(2025, 4, 15), "DMIC-N03-P01", 42.50, 78.0, 70.0, -0.05, 4.0, SatelliteAlertFlag.AMBER_VARIANCE_GT5, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N03-P02-250215", LocalDate.of(2025, 2, 15), "DMIC-N03-P02", 36.10, 70.0, 62.0, -0.02, 6.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N03-P02-250315", LocalDate.of(2025, 3, 15), "DMIC-N03-P02", 37.80, 72.0, 64.0, -0.03, 4.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.PLANET_LABS);
        seedScene(projectId, "SCN-N03-P02-250415", LocalDate.of(2025, 4, 15), "DMIC-N03-P02", 38.20, 73.0, 65.0, -0.03, 7.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N03-P03-250215", LocalDate.of(2025, 2, 15), "DMIC-N03-P03", 24.50, 60.0, 55.0, -0.01, 12.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N03-P03-250315", LocalDate.of(2025, 3, 15), "DMIC-N03-P03", 26.20, 62.0, 56.0, -0.01, 9.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.PLANET_LABS);
        seedScene(projectId, "SCN-N03-P03-250415", LocalDate.of(2025, 4, 15), "DMIC-N03-P03", 28.50, 64.0, 58.0, -0.02, 6.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N04-P01-250215", LocalDate.of(2025, 2, 15), "DMIC-N04-P01", 50.00, 66.0, 60.0, -0.05, 15.0, SatelliteAlertFlag.RED_VARIANCE_GT10, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N04-P01-250315", LocalDate.of(2025, 3, 15), "DMIC-N04-P01", 52.50, 68.0, 62.0, -0.06, 10.0, SatelliteAlertFlag.RED_VARIANCE_GT10, SatelliteImageSource.PLANET_LABS);
        // Scenario 7 — Excel SCN-N04-250328: AI 50 vs Contractor 62 = variance 12% → RED_VARIANCE_GT10
        seedScene(projectId, "SCN-N04-250328", LocalDate.of(2025, 3, 28), "DMIC-N04-P01", 50.00, 67.0, 61.0, -0.06, 9.0, SatelliteAlertFlag.RED_VARIANCE_GT10, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N04-P01-250415", LocalDate.of(2025, 4, 15), "DMIC-N04-P01", 55.30, 70.0, 64.0, -0.08, 8.0, SatelliteAlertFlag.RED_VARIANCE_GT10, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N04-P02-250215", LocalDate.of(2025, 2, 15), "DMIC-N04-P02", 44.00, 74.0, 66.0, -0.04, 5.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N04-P02-250315", LocalDate.of(2025, 3, 15), "DMIC-N04-P02", 46.20, 76.0, 68.0, -0.05, 4.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.PLANET_LABS);
        seedScene(projectId, "SCN-N04-P02-250415", LocalDate.of(2025, 4, 15), "DMIC-N04-P02", 48.00, 77.0, 69.0, -0.05, 3.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N05-P01-250215", LocalDate.of(2025, 2, 15), "DMIC-N05-P01", 32.80, 71.0, 63.0, -0.03, 7.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N05-P01-250315", LocalDate.of(2025, 3, 15), "DMIC-N05-P01", 34.50, 73.0, 65.0, -0.04, 5.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.PLANET_LABS);
        seedScene(projectId, "SCN-N05-P01-250415", LocalDate.of(2025, 4, 15), "DMIC-N05-P01", 35.80, 74.0, 66.0, -0.04, 6.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N05-P02-250315", LocalDate.of(2025, 3, 15), "DMIC-N05-P02", 20.50, 58.0, 52.0, -0.01, 11.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N05-P02-250415", LocalDate.of(2025, 4, 15), "DMIC-N05-P02", 22.00, 60.0, 54.0, -0.01, 9.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.PLANET_LABS);

        seedScene(projectId, "SCN-N06-P01-250215", LocalDate.of(2025, 2, 15), "DMIC-N06-P01", 58.00, 80.0, 72.0, -0.06, 4.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N06-P01-250315", LocalDate.of(2025, 3, 15), "DMIC-N06-P01", 60.20, 82.0, 74.0, -0.07, 3.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.PLANET_LABS);
        seedScene(projectId, "SCN-N06-P01-250415", LocalDate.of(2025, 4, 15), "DMIC-N06-P01", 62.40, 84.0, 76.0, -0.08, 2.0, SatelliteAlertFlag.GREEN, SatelliteImageSource.ISRO_CARTOSAT);

        seedScene(projectId, "SCN-N06-P02-250315", LocalDate.of(2025, 3, 15), "DMIC-N06-P02", 13.80, 50.0, 45.0, -0.01, 14.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.ISRO_CARTOSAT);
        seedScene(projectId, "SCN-N06-P02-250415", LocalDate.of(2025, 4, 15), "DMIC-N06-P02", 15.20, 52.0, 47.0, -0.01, 12.0, SatelliteAlertFlag.AMBER_IDLE_ZONE, SatelliteImageSource.PLANET_LABS);

        log.info("[IC-PMS Phase C] seeded 26 satellite scenes");

        // --- RA bills (16 bills, Jan-Apr 2025) -------------------------------------
        // Columns: billNumber, wbsPackageCode, periodTo, grossCrores, claimPct, deductionFactor
        seedBill(projectId, "DMIC-N03-P01-RA-001", "DMIC-N03-P01", LocalDate.of(2025, 1, 31), 55.00, new BigDecimal("10.00"));
        seedBill(projectId, "DMIC-N03-P01-RA-002", "DMIC-N03-P01", LocalDate.of(2025, 2, 28), 82.50, new BigDecimal("15.00"));
        seedBill(projectId, "DMIC-N03-P01-RA-003", "DMIC-N03-P01", LocalDate.of(2025, 3, 31), 115.00, new BigDecimal("21.00"));
        seedBill(projectId, "DMIC-N03-P01-RA-004", "DMIC-N03-P01", LocalDate.of(2025, 3, 31), 165.00, new BigDecimal("30.00"));
        seedBill(projectId, "DMIC-N03-P01-RA-005", "DMIC-N03-P01", LocalDate.of(2025, 4, 15), 220.00, new BigDecimal("40.00"));
        // Scenario bill — claim 49.50 vs AI 42.50 → variance 7 → HOLD_VARIANCE
        seedBill(projectId, "DMIC-N03-P01-RA-006", "DMIC-N03-P01", LocalDate.of(2025, 4, 30), 272.25, new BigDecimal("49.50"));

        seedBill(projectId, "DMIC-N03-P02-RA-001", "DMIC-N03-P02", LocalDate.of(2025, 2, 28), 58.00, new BigDecimal("12.50"));
        seedBill(projectId, "DMIC-N03-P02-RA-002", "DMIC-N03-P02", LocalDate.of(2025, 3, 31), 130.00, new BigDecimal("28.00"));
        seedBill(projectId, "DMIC-N03-P02-RA-003", "DMIC-N03-P02", LocalDate.of(2025, 4, 30), 177.20, new BigDecimal("38.00"));

        seedBill(projectId, "DMIC-N04-P01-RA-001", "DMIC-N04-P01", LocalDate.of(2025, 2, 28), 98.50, new BigDecimal("25.00"));
        // N04-P01 contractor overclaim — claim 60 vs AI 55.30 → variance 4.7 → PASS (within tolerance, but SPI/CPI still AT_RISK)
        seedBill(projectId, "DMIC-N04-P01-RA-002", "DMIC-N04-P01", LocalDate.of(2025, 4, 30), 247.50, new BigDecimal("60.00"));

        seedBill(projectId, "DMIC-N04-P02-RA-001", "DMIC-N04-P02", LocalDate.of(2025, 3, 31), 86.00, new BigDecimal("30.00"));
        seedBill(projectId, "DMIC-N04-P02-RA-002", "DMIC-N04-P02", LocalDate.of(2025, 4, 30), 138.00, new BigDecimal("48.00"));

        seedBill(projectId, "DMIC-N05-P01-RA-001", "DMIC-N05-P01", LocalDate.of(2025, 3, 31), 100.50, new BigDecimal("25.00"));
        seedBill(projectId, "DMIC-N05-P01-RA-002", "DMIC-N05-P01", LocalDate.of(2025, 4, 30), 143.20, new BigDecimal("35.80"));

        seedBill(projectId, "DMIC-N06-P01-RA-001", "DMIC-N06-P01", LocalDate.of(2025, 4, 30), 269.88, new BigDecimal("62.40"));

        log.info("[IC-PMS Phase C] seeded 16 RA bills — satellite gate evaluated on each");
    }

    private void seedScene(UUID projectId, String sceneId, LocalDate date, String packageCode,
                           double aiProgress, double cvi, double edi, double ndviChange,
                           double cloudCover, SatelliteAlertFlag alert, SatelliteImageSource source) {
        SatelliteImage img = new SatelliteImage();
        img.setProjectId(projectId);
        img.setSceneId(sceneId);
        img.setImageName(sceneId);
        img.setDescription("IC-PMS M3 seed scene for " + packageCode);
        img.setCaptureDate(date);
        img.setSource(source);
        img.setResolution("0.5m");
        img.setFilePath("s3://icpms-satellite/" + sceneId + ".tif");
        img.setFileSize(524288000L);
        img.setMimeType("image/tiff");
        img.setCloudCoverPercent(cloudCover);
        img.setStatus(SatelliteImageStatus.READY);
        SatelliteImage savedImg = satelliteImageRepository.save(img);

        ConstructionProgressSnapshot snap = new ConstructionProgressSnapshot();
        snap.setProjectId(projectId);
        snap.setCaptureDate(date);
        snap.setSatelliteImageId(savedImg.getId());
        snap.setWbsPackageCode(packageCode);
        snap.setAiProgressPercent(aiProgress);
        snap.setDerivedProgressPercent(aiProgress);
        snap.setCvi(cvi);
        snap.setEdi(edi);
        snap.setNdviChange(ndviChange);
        snap.setAlertFlag(alert);
        snap.setAnalysisMethod(ProgressAnalysisMethod.AI_SEGMENTATION);
        snap.setRemarks("Seeded for Excel M3 parity");
        snapshotRepository.save(snap);
    }

    private void seedBill(UUID projectId, String billNumber, String wbsPackageCode,
                          LocalDate periodTo, double grossCrores, BigDecimal claimPct) {
        RaBill bill = new RaBill();
        bill.setProjectId(projectId);
        bill.setBillNumber(billNumber);
        bill.setWbsPackageCode(wbsPackageCode);
        bill.setBillPeriodFrom(periodTo.minusMonths(1).plusDays(1));
        bill.setBillPeriodTo(periodTo);
        BigDecimal gross = BigDecimal.valueOf(grossCrores);
        bill.setGrossAmount(gross);

        // CPWD-style deductions
        BigDecimal mobAdvance = gross.multiply(new BigDecimal("0.10"));  // 10% mob advance recovery
        BigDecimal retention = gross.multiply(new BigDecimal("0.05"));    // 5% retention
        BigDecimal tds = gross.multiply(new BigDecimal("0.02"));          // 2% TDS
        BigDecimal gst = gross.multiply(new BigDecimal("0.18"));          // 18% GST
        bill.setMobAdvanceRecovery(mobAdvance);
        bill.setRetention5Pct(retention);
        bill.setTds2Pct(tds);
        bill.setGst18Pct(gst);
        BigDecimal totalDeductions = mobAdvance.add(retention).add(tds).add(gst);
        bill.setDeductions(totalDeductions);
        bill.setNetAmount(gross.subtract(totalDeductions));

        bill.setContractorClaimedPercent(claimPct);
        bill.setStatus(RaBill.RaBillStatus.SUBMITTED);
        bill.setSubmittedDate(periodTo);

        satelliteGateService.evaluate(bill);
        raBillRepository.save(bill);
    }
}
