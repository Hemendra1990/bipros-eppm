package com.bipros.api.config.seeder;

import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractAttachment;
import com.bipros.contract.domain.model.ContractAttachmentType;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.CostAccountRepository;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.model.DrawingStatus;
import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.model.RfiStatus;
import com.bipros.document.domain.model.Transmittal;
import com.bipros.document.domain.model.TransmittalStatus;
import com.bipros.document.domain.repository.DrawingRegisterRepository;
import com.bipros.document.domain.repository.RfiRegisterRepository;
import com.bipros.document.domain.repository.TransmittalRepository;
import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import com.bipros.gis.domain.model.GisLayer;
import com.bipros.gis.domain.model.GisLayerType;
import com.bipros.gis.domain.model.ProgressAnalysisMethod;
import com.bipros.gis.domain.model.SatelliteAlertFlag;
import com.bipros.gis.domain.model.SatelliteImage;
import com.bipros.gis.domain.model.SatelliteImageSource;
import com.bipros.gis.domain.model.SatelliteImageStatus;
import com.bipros.gis.domain.model.WbsPolygon;
import com.bipros.gis.domain.repository.ConstructionProgressSnapshotRepository;
import com.bipros.gis.domain.repository.GisLayerRepository;
import com.bipros.gis.domain.repository.SatelliteImageRepository;
import com.bipros.gis.domain.repository.WbsPolygonRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskResponse;
import com.bipros.risk.domain.model.RiskResponseType;
import com.bipros.risk.domain.model.RiskTrigger;
import com.bipros.risk.domain.repository.RiskRepository;
import com.bipros.risk.domain.repository.RiskResponseRepository;
import com.bipros.risk.domain.repository.RiskTriggerRepository;
import com.bipros.scheduling.domain.model.RiskLevel;
import com.bipros.scheduling.domain.model.ScheduleHealthIndex;
import com.bipros.scheduling.domain.model.ScheduleScenario;
import com.bipros.scheduling.domain.model.ScenarioStatus;
import com.bipros.scheduling.domain.model.ScenarioType;
import com.bipros.scheduling.domain.repository.ScheduleHealthIndexRepository;
import com.bipros.scheduling.domain.repository.ScheduleScenarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Supplemental seeder for the NH-48 road project that fills modules not covered by
 * {@link NhaiRoadProjectSeeder} or the SQL report bundle:
 * <ul>
 *   <li>GIS — layers, WBS polygons, construction progress snapshots, satellite images</li>
 *   <li>Contracts — attachment rows referencing the copied PDFs</li>
 *   <li>Documents — drawing register, RFI register, transmittals</li>
 *   <li>Risk — responses and triggers for the seeded risk register rows</li>
 *   <li>Scheduling — schedule scenarios and health index</li>
 *   <li>Cost — cost accounts</li>
 * </ul>
 *
 * <p>Runs at {@code @Order(150)} so the project, WBS, activities, contracts and risks
 * already exist. Idempotent via sentinel checks on each module.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(150)
@RequiredArgsConstructor
public class NhaiRoadProjectSupplementalSeeder implements CommandLineRunner {

  private static final String PROJECT_CODE = "BIPROS/NHAI/RJ/2025/001";
  private static final String CONTRACT_NUMBER_MAIN = "NH48-MAIN-2024-001";

  // ── Repositories ───────────────────────────────────────────────
  private final ProjectRepository projectRepository;
  private final WbsNodeRepository wbsNodeRepository;
  private final ContractRepository contractRepository;
  private final ContractAttachmentRepository contractAttachmentRepository;
  private final GisLayerRepository gisLayerRepository;
  private final WbsPolygonRepository wbsPolygonRepository;
  private final ConstructionProgressSnapshotRepository progressSnapshotRepository;
  private final SatelliteImageRepository satelliteImageRepository;
  private final DrawingRegisterRepository drawingRegisterRepository;
  private final RfiRegisterRepository rfiRegisterRepository;
  private final TransmittalRepository transmittalRepository;
  private final RiskRepository riskRepository;
  private final RiskResponseRepository riskResponseRepository;
  private final RiskTriggerRepository riskTriggerRepository;
  private final ScheduleScenarioRepository scheduleScenarioRepository;
  private final ScheduleHealthIndexRepository scheduleHealthIndexRepository;
  private final CostAccountRepository costAccountRepository;

  @Override
  public void run(String... args) {
    Project project = projectRepository.findByCode(PROJECT_CODE).orElse(null);
    if (project == null) {
      log.info("[NH-48-SUPP] project '{}' not found — skipping supplemental seeder", PROJECT_CODE);
      return;
    }
    UUID projectId = project.getId();

    log.info("[NH-48-SUPP] starting supplemental seeding for '{}'", PROJECT_CODE);

    seedGisData(projectId);
    seedContractAttachments(projectId);
    seedDocumentRegisters(projectId);
    seedRiskResponsesAndTriggers(projectId);
    seedScheduleScenarios(projectId);
    seedCostAccounts();

    log.info("[NH-48-SUPP] supplemental seeding completed");
  }

  // ────────────────────────── GIS ───────────────────────────────
  private void seedGisData(UUID projectId) {
    if (!gisLayerRepository.findByProjectIdOrderBySortOrder(projectId).isEmpty()) {
      log.info("[NH-48-SUPP] GIS layers already seeded — skipping");
      return;
    }

    // 1. GIS Layers
    GisLayer wbsLayer = new GisLayer();
    wbsLayer.setProjectId(projectId);
    wbsLayer.setLayerName("WBS Packages");
    wbsLayer.setLayerType(GisLayerType.WBS_POLYGON);
    wbsLayer.setDescription("NH-48 WBS package boundaries along the 20 km corridor");
    wbsLayer.setIsVisible(true);
    wbsLayer.setOpacity(0.7);
    wbsLayer.setSortOrder(1);

    GisLayer satLayer = new GisLayer();
    satLayer.setProjectId(projectId);
    satLayer.setLayerName("Satellite Imagery");
    satLayer.setLayerType(GisLayerType.SATELLITE_OVERLAY);
    satLayer.setDescription("Sentinel-2 satellite overlays for progress monitoring");
    satLayer.setIsVisible(true);
    satLayer.setOpacity(0.85);
    satLayer.setSortOrder(2);
    gisLayerRepository.save(wbsLayer);
    gisLayerRepository.save(satLayer);
    log.info("[NH-48-SUPP] seeded {} GIS layers", 2);

    // 2. WBS Polygons — simple corridor rectangles for each WBS package
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    List<WbsPolygon> polygons = new ArrayList<>();

    Map<String, String> wbsNames = Map.of(
        "WBS-1", "Earthwork",
        "WBS-2", "Sub-base",
        "WBS-3", "Bituminous",
        "WBS-4", "Drainage",
        "WBS-5", "Road Furniture",
        "WBS-6", "Bridges",
        "WBS-7", "Miscellaneous"
    );

    var wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId).stream()
        .filter(w -> w.getCode() != null && w.getCode().startsWith("WBS-"))
        .filter(w -> !"WBS-ROOT".equals(w.getCode()))
        .sorted(java.util.Comparator.comparing(w -> w.getCode()))
        .toList();

    double baseLat = 26.82;
    double baseLon = 76.25;
    double latSpan = 0.025;
    double lonSpan = 0.018;

    for (int i = 0; i < wbsNodes.size(); i++) {
      var wbs = wbsNodes.get(i);
      double minLat = baseLat + i * latSpan;
      double maxLat = minLat + latSpan;
      double minLon = baseLon;
      double maxLon = baseLon + lonSpan;

      Coordinate[] coords = new Coordinate[]{
          new Coordinate(minLon, minLat),
          new Coordinate(maxLon, minLat),
          new Coordinate(maxLon, maxLat),
          new Coordinate(minLon, maxLat),
          new Coordinate(minLon, minLat)
      };
      Polygon poly = gf.createPolygon(coords);

      WbsPolygon wp = new WbsPolygon();
      wp.setProjectId(projectId);
      wp.setWbsNodeId(wbs.getId());
      wp.setLayerId(wbsLayer.getId());
      wp.setWbsCode(wbs.getCode());
      wp.setWbsName(wbsNames.getOrDefault(wbs.getCode(), wbs.getName()));
      wp.setPolygon(poly);
      wp.setCenterLatitude((minLat + maxLat) / 2.0);
      wp.setCenterLongitude((minLon + maxLon) / 2.0);
      wp.setAreaInSqMeters(2_800_000.0);
      wp.setFillColor(switch (wbs.getCode()) {
        case "WBS-1" -> "#8B4513";
        case "WBS-2" -> "#DAA520";
        case "WBS-3" -> "#2F4F4F";
        case "WBS-4" -> "#4682B4";
        case "WBS-5" -> "#32CD32";
        case "WBS-6" -> "#FF6347";
        default -> "#808080";
      });
      wp.setStrokeColor("#000000");
      polygons.add(wp);
    }
    wbsPolygonRepository.saveAll(polygons);
    log.info("[NH-48-SUPP] seeded {} WBS polygons", polygons.size());

    // 3. Construction Progress Snapshots
    List<ConstructionProgressSnapshot> snapshots = new ArrayList<>();
    LocalDate[] dates = {LocalDate.of(2025, 4, 15), LocalDate.of(2025, 7, 20), LocalDate.of(2025, 10, 10), LocalDate.of(2026, 1, 25), LocalDate.of(2026, 4, 18)};
    double[][] progressData = {
        {8.0, 7.5, 0.5, 65.0, 62.0, 3.0},
        {30.0, 28.0, 2.0, 72.0, 68.0, 4.0},
        {50.0, 47.0, 3.0, 78.0, 74.0, 4.0},
        {72.0, 68.0, 4.0, 82.0, 78.0, 4.0},
        {88.0, 83.0, 5.0, 85.0, 80.0, 5.0}
    };
    SatelliteAlertFlag[] alertFlags = {
        SatelliteAlertFlag.GREEN,
        SatelliteAlertFlag.GREEN,
        SatelliteAlertFlag.AMBER_VARIANCE_GT5,
        SatelliteAlertFlag.AMBER_VARIANCE_GT5,
        SatelliteAlertFlag.RED_VARIANCE_GT10
    };

    for (int i = 0; i < dates.length; i++) {
      var data = progressData[i];
      ConstructionProgressSnapshot s = new ConstructionProgressSnapshot();
      s.setProjectId(projectId);
      s.setCaptureDate(dates[i]);
      s.setDerivedProgressPercent(data[0]);
      s.setContractorClaimedPercent(data[1]);
      s.setVariancePercent(data[2]);
      s.setAiProgressPercent(data[3]);
      s.setCvi(data[4]);
      s.setEdi(data[5]);
      s.setNdviChange(0.05 * (i - 2));
      s.setWbsPackageCode("NH48-MAIN");
      s.setAlertFlag(alertFlags[i]);
      s.setAnalysisMethod(ProgressAnalysisMethod.AI_SEGMENTATION);
      s.setAnalyzerId("claude-vision:claude-sonnet-4-6");
      s.setAnalysisDurationMs(4500 + i * 300);
      s.setAnalysisCostMicros(120000L + i * 15000L);
      s.setRemarks("Progress snapshot from satellite imagery analysis — " + dates[i]);
      snapshots.add(s);
    }
    progressSnapshotRepository.saveAll(snapshots);
    log.info("[NH-48-SUPP] seeded {} construction progress snapshots", snapshots.size());

    // 4. Satellite Images
    seedSatelliteImages(projectId, satLayer.getId());
  }

  private void seedSatelliteImages(UUID projectId, UUID layerId) {
    Path satRoot = Paths.get("data", "satellite");
    String[] files = {
        "WhatsApp Image 2026-04-22 at 00.01.17.jpeg",
        "WhatsApp Image 2026-04-22 at 00.01.28.jpeg",
        "WhatsApp Image 2026-04-22 at 00.01.41.jpeg",
        "WhatsApp Image 2026-04-22 at 00.01.50.jpeg",
        "WhatsApp Image 2026-04-22 at 00.02.03.jpeg"
    };
    LocalDate[] captureDates = {
        LocalDate.of(2025, 4, 15),
        LocalDate.of(2025, 7, 20),
        LocalDate.of(2025, 10, 10),
        LocalDate.of(2026, 1, 25),
        LocalDate.of(2026, 4, 18)
    };

    List<SatelliteImage> images = new ArrayList<>();
    for (int i = 0; i < files.length; i++) {
      Path p = satRoot.resolve(files[i]);
      File f = p.toFile();
      if (!f.exists()) {
        log.warn("[NH-48-SUPP] satellite image not found: {}", p);
        continue;
      }
      SatelliteImage img = new SatelliteImage();
      img.setProjectId(projectId);
      img.setLayerId(layerId);
      img.setSceneId("SCN-NH48-" + String.format("%02d", i + 1));
      img.setCloudCoverPercent(5.0 + i * 3.0);
      img.setImageName("NH-48 Corridor Snapshot " + (i + 1));
      img.setDescription("Satellite imagery for progress monitoring — capture " + (i + 1));
      img.setCaptureDate(captureDates[i]);
      img.setSource(SatelliteImageSource.SENTINEL_HUB);
      img.setResolution("10 m");
      img.setBoundingBoxGeoJson("{\"type\":\"Polygon\",\"coordinates\":[[[76.25,26.82],[76.268,26.82],[76.268,26.995],[76.25,26.995],[76.25,26.82]]]}");
      img.setFilePath(p.toString());
      img.setFileSize(f.length());
      img.setMimeType("image/jpeg");
      img.setNorthBound(26.995);
      img.setSouthBound(26.82);
      img.setEastBound(76.268);
      img.setWestBound(76.25);
      img.setStatus(SatelliteImageStatus.READY);
      images.add(img);
    }
    satelliteImageRepository.saveAll(images);
    log.info("[NH-48-SUPP] seeded {} satellite images", images.size());
  }

  // ───────────────────── Contract Attachments ───────────────────
  private void seedContractAttachments(UUID projectId) {
    Contract mainContract = contractRepository.findByContractNumber(CONTRACT_NUMBER_MAIN).orElse(null);
    if (mainContract == null) {
      log.warn("[NH-48-SUPP] main contract not found in DB — creating fallback contract");
      mainContract = new Contract();
      mainContract.setProjectId(projectId);
      mainContract.setContractNumber(CONTRACT_NUMBER_MAIN);
      mainContract.setLoaNumber("NHAI/RJ/NH48/LOA/2024-148");
      mainContract.setContractorName("ABC Infracon Pvt Ltd");
      mainContract.setContractorCode("ABC-GSTN-08AAACA1234L1Z8");
      mainContract.setContractValue(new BigDecimal("4850000000.00"));
      mainContract.setLoaDate(LocalDate.of(2024, 11, 28));
      mainContract.setStartDate(LocalDate.of(2024, 12, 15));
      mainContract.setCompletionDate(LocalDate.of(2026, 12, 31));
      mainContract.setDlpMonths(24);
      mainContract.setLdRate(0.10);
      mainContract.setStatus(com.bipros.contract.domain.model.ContractStatus.ACTIVE_AT_RISK);
      mainContract.setContractType(com.bipros.contract.domain.model.ContractType.EPC_LUMP_SUM_FIDIC_YELLOW);
      mainContract.setCurrency("INR");
      mainContract.setWbsPackageCode("NH48-MAIN");
      mainContract.setPackageDescription("NH-48 Rajasthan 4-lane widening — 20 km main carriageway works (Ch 145+000 to Ch 165+000).");
      mainContract.setSpi(new BigDecimal("0.901"));
      mainContract.setCpi(new BigDecimal("0.947"));
      mainContract.setPhysicalProgressAi(new BigDecimal("88.25"));
      mainContract.setCumulativeRaBillsCrores(new BigDecimal("159.00"));
      mainContract.setVoNumbersIssued(2);
      mainContract.setVoValueCrores(new BigDecimal("15.70"));
      mainContract.setPerformanceScore(new BigDecimal("78.50"));
      mainContract = contractRepository.save(mainContract);
      log.info("[NH-48-SUPP] created fallback main contract '{}'", mainContract.getId());
    }
    if (!contractAttachmentRepository.findByContractIdOrderByCreatedAtDesc(mainContract.getId()).isEmpty()) {
      log.info("[NH-48-SUPP] contract attachments already seeded — skipping");
      return;
    }

    List<ContractAttachment> attachments = new ArrayList<>();
    Path docRoot = Paths.get("storage", "documents");

    record AttachSpec(String fileName, ContractAttachmentType type, String description) {}
    AttachSpec[] specs = {
        new AttachSpec("DRG NH48 002 Pavement Design.pdf", ContractAttachmentType.DRAWING,
            "Approved pavement design drawing — Flexible pavement IRC:37-2018, CBR 8%"),
        new AttachSpec("ITP NH48 001 Earthwork.pdf", ContractAttachmentType.TEST_REPORT,
            "Inspection and Test Plan for earthwork activities"),
        new AttachSpec("MS NH48 003 Bituminous Paving.pdf", ContractAttachmentType.OTHER,
            "Method Statement for bituminous paving — DBM and BC layers"),
        new AttachSpec("PLAN NH48 004 Project Quality Plan.pdf", ContractAttachmentType.CERTIFICATE,
            "Project Quality Plan approved by NHAI"),
        new AttachSpec("SPEC NH48 002 GSB WMM.pdf", ContractAttachmentType.OTHER,
            "Technical specifications for GSB and WMM works"),
        new AttachSpec("Risk Master Metadata Road Construction.docx", ContractAttachmentType.OTHER,
            "Risk Master Metadata document for road construction projects")
    };

    for (AttachSpec spec : specs) {
      Path p = docRoot.resolve(spec.fileName);
      File f = p.toFile();
      if (!f.exists()) {
        log.warn("[NH-48-SUPP] contract attachment file not found: {}", p);
        continue;
      }
      ContractAttachment a = new ContractAttachment();
      a.setProjectId(projectId);
      a.setContractId(mainContract.getId());
      a.setEntityType(AttachmentEntityType.CONTRACT);
      a.setEntityId(mainContract.getId());
      a.setFileName(spec.fileName);
      a.setFileSize(f.length());
      a.setMimeType(spec.fileName.endsWith(".pdf") ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
      a.setFilePath(p.toString());
      a.setAttachmentType(spec.type);
      a.setDescription(spec.description);
      a.setUploadedBy("admin@bipros.local");
      a.setUploadedAt(Instant.now());
      attachments.add(a);
    }
    contractAttachmentRepository.saveAll(attachments);
    log.info("[NH-48-SUPP] seeded {} contract attachments", attachments.size());
  }

  // ───────────────────── Document Registers ─────────────────────
  private void seedDocumentRegisters(UUID projectId) {
    if (!drawingRegisterRepository.findByProjectId(projectId).isEmpty()) {
      log.info("[NH-48-SUPP] drawing register already seeded for this project — skipping document registers");
      return;
    }

    // Drawing Register
    List<DrawingRegister> drawings = new ArrayList<>();
    drawings.add(buildDrawing(projectId, "DRG/NH48/001", "General Arrangement – Road Cross Section",
        DrawingDiscipline.CIVIL, "R2", LocalDate.of(2025, 1, 15), DrawingStatus.IFC, "NH48-MAIN", "1:500"));
    drawings.add(buildDrawing(projectId, "DRG/NH48/002", "Pavement Design – Flexible Pavement",
        DrawingDiscipline.CIVIL, "R1", LocalDate.of(2025, 1, 15), DrawingStatus.IFC, "NH48-MAIN", "1:200"));
    drawings.add(buildDrawing(projectId, "DRG/NH48/003", "Bridge General Arrangement – Pier & Abutment",
        DrawingDiscipline.STRUCTURAL, "R0", LocalDate.of(2025, 2, 20), DrawingStatus.IFA, "NH48-MAIN", "1:100"));
    drawings.add(buildDrawing(projectId, "DRG/NH48/004", "Drainage Longitudinal Section",
        DrawingDiscipline.CIVIL, "R1", LocalDate.of(2025, 3, 5), DrawingStatus.IFC, "NH48-MAIN", "1:1000"));
    drawings.add(buildDrawing(projectId, "DRG/NH48/005", "Road Furniture Layout – Signage & Marking",
        DrawingDiscipline.CIVIL, "R0", LocalDate.of(2025, 8, 10), DrawingStatus.PRELIMINARY, "NH48-MAIN", "1:500"));
    drawingRegisterRepository.saveAll(drawings);
    log.info("[NH-48-SUPP] seeded {} drawing register entries", drawings.size());

    // RFI Register
    List<RfiRegister> rfis = new ArrayList<>();
    rfis.add(buildRfi(projectId, "RFI-NH48-001", "Earthwork CBR retest at Ch 147+200",
        "Field CBR at Ch 147+200 recorded 6.2% against design 8%. Request retest protocol.",
        "ABC Infracon — Site Engineer", "NHAI PMC — DGM", LocalDate.of(2025, 3, 10),
        LocalDate.of(2025, 3, 20), LocalDate.of(2025, 3, 18), RfiStatus.CLOSED, RfiPriority.HIGH,
        "Retest conducted 2025-03-15 by IRC-accredited lab. CBR 8.1% achieved after moisture correction."));
    rfis.add(buildRfi(projectId, "RFI-NH48-002", "Bridge pier P2 soft strata confirmation",
        "N-value < 6 encountered at 6 m depth during pier P2 excavation. Confirm pile cap revision.",
        "ABC Infracon — Project Manager", "IIT-Roorkee Peer Review", LocalDate.of(2025, 4, 5),
        LocalDate.of(2025, 5, 15), LocalDate.of(2025, 5, 10), RfiStatus.CLOSED, RfiPriority.CRITICAL,
        "Peer review confirms 4 m deeper pile cap + 12 additional BCS piles. VO-001 approved."));
    rfis.add(buildRfi(projectId, "RFI-NH48-003", "Bitumen VG-30 temperature tolerance",
        "IOCL Panipat turnaround maintenance may affect VG-30 supply. Confirm alternate source acceptance.",
        "ABC Infracon — Materials Manager", "NHAI PMC — DGM", LocalDate.of(2025, 6, 22),
        LocalDate.of(2025, 7, 10), null, RfiStatus.OPEN, RfiPriority.MEDIUM, null));
    rfis.add(buildRfi(projectId, "RFI-NH48-004", "Utility shifting scope at Ch 157+800",
        "PWD Rajasthan directive mandates OHL-to-UG conversion. Confirm VO-002 scope and rates.",
        "ABC Infracon — Planning Manager", "NHAI RO Jaipur", LocalDate.of(2026, 2, 20),
        LocalDate.of(2026, 3, 15), null, RfiStatus.OPEN, RfiPriority.HIGH, null));
    rfiRegisterRepository.saveAll(rfis);
    log.info("[NH-48-SUPP] seeded {} RFI register entries", rfis.size());

    // Transmittals
    List<Transmittal> transmittals = new ArrayList<>();
    transmittals.add(buildTransmittal(projectId, "TRM-NH48-001", "Submission of Method Statement — Earthwork",
        "ABC Infracon Pvt Ltd", "NHAI PMC — DGM", LocalDate.of(2025, 1, 20),
        LocalDate.of(2025, 1, 30), TransmittalStatus.ACKNOWLEDGED,
        "Method statement for earthwork excavation and embankment construction approved."));
    transmittals.add(buildTransmittal(projectId, "TRM-NH48-002", "Submission of ITP — GSB & WMM",
        "ABC Infracon Pvt Ltd", "NHAI PMC — DGM", LocalDate.of(2025, 3, 5),
        LocalDate.of(2025, 3, 15), TransmittalStatus.ACKNOWLEDGED,
        "Inspection and Test Plan for GSB and WMM layers approved with minor comments."));
    transmittals.add(buildTransmittal(projectId, "TRM-NH48-003", "Monthly Progress Report — March 2025",
        "ABC Infracon Pvt Ltd", "NHAI RO Jaipur", LocalDate.of(2025, 4, 5),
        LocalDate.of(2025, 4, 15), TransmittalStatus.RECEIVED,
        "MPR submitted with physical progress 8.2% and financial progress 7.8%."));
    transmittals.add(buildTransmittal(projectId, "TRM-NH48-004", "VO-002 Proposal — Utility Shifting",
        "ABC Infracon Pvt Ltd", "NHAI PMC — DGM", LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 3, 20), TransmittalStatus.SENT,
        "Variation order proposal for 132 kV HT line underground conversion at Ch 157+800."));
    transmittalRepository.saveAll(transmittals);
    log.info("[NH-48-SUPP] seeded {} transmittals", transmittals.size());
  }

  private DrawingRegister buildDrawing(UUID projectId, String number, String title,
                                       DrawingDiscipline discipline, String revision, LocalDate date,
                                       DrawingStatus status, String pkg, String scale) {
    DrawingRegister d = new DrawingRegister();
    d.setProjectId(projectId);
    d.setDrawingNumber(number);
    d.setTitle(title);
    d.setDiscipline(discipline);
    d.setRevision(revision);
    d.setRevisionDate(date);
    d.setStatus(status);
    d.setPackageCode(pkg);
    d.setScale(scale);
    return d;
  }

  private RfiRegister buildRfi(UUID projectId, String number, String subject, String description,
                               String raisedBy, String assignedTo, LocalDate raisedDate,
                               LocalDate dueDate, LocalDate closedDate, RfiStatus status,
                               RfiPriority priority, String response) {
    RfiRegister r = new RfiRegister();
    r.setProjectId(projectId);
    r.setRfiNumber(number);
    r.setSubject(subject);
    r.setDescription(description);
    r.setRaisedBy(raisedBy);
    r.setAssignedTo(assignedTo);
    r.setRaisedDate(raisedDate);
    r.setDueDate(dueDate);
    r.setClosedDate(closedDate);
    r.setStatus(status);
    r.setPriority(priority);
    r.setResponse(response);
    return r;
  }

  private Transmittal buildTransmittal(UUID projectId, String number, String subject,
                                       String fromParty, String toParty, LocalDate sentDate,
                                       LocalDate dueDate, TransmittalStatus status, String remarks) {
    Transmittal t = new Transmittal();
    t.setProjectId(projectId);
    t.setTransmittalNumber(number);
    t.setSubject(subject);
    t.setFromParty(fromParty);
    t.setToParty(toParty);
    t.setSentDate(sentDate);
    t.setDueDate(dueDate);
    t.setStatus(status);
    t.setRemarks(remarks);
    return t;
  }

  // ───────────────────── Risk Responses & Triggers ──────────────
  private void seedRiskResponsesAndTriggers(UUID projectId) {
    List<Risk> risks = riskRepository.findByProjectId(projectId);
    if (risks.isEmpty()) {
      log.warn("[NH-48-SUPP] no risks found for project — skipping responses/triggers");
      return;
    }

    if (riskResponseRepository.count() > 0) {
      log.info("[NH-48-SUPP] risk responses already seeded — skipping");
      return;
    }

    Map<String, Risk> riskByCode = risks.stream()
        .filter(r -> r.getCode() != null)
        .collect(Collectors.toMap(Risk::getCode, r -> r));

    List<RiskResponse> responses = new ArrayList<>();
    List<RiskTrigger> triggers = new ArrayList<>();

    // R-001 Land acquisition
    if (riskByCode.containsKey("R-001")) {
      UUID rid = riskByCode.get("R-001").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Engage CALA for arbitration; phase construction around acquired stretches; weekly ROW review with Revenue Authority.",
          null, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 5, 15), new BigDecimal("2500000"), new BigDecimal("1800000"), "COMPLETED"));
      responses.add(buildResponse(rid, RiskResponseType.TRANSFER,
          "Escrow arrangement with land owners for early handover incentive.",
          null, LocalDate.of(2025, 6, 1), null, new BigDecimal("5000000"), null, "PLANNED"));
      triggers.add(buildTrigger(rid, projectId, "ROW clearance < 80% at mobilisation minus 30 days",
          RiskTrigger.TriggerType.SCHEDULE_DELAY, 80.0, 75.0, true, RiskTrigger.EscalationLevel.RED, "PROJECT_MANAGER,ADMIN"));
    }

    // R-002 Environmental clearance
    if (riskByCode.containsKey("R-002")) {
      UUID rid = riskByCode.get("R-002").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Parallel SEIAA application; alternate quarry source pre-qualified; environmental consultant on retainer.",
          null, LocalDate.of(2025, 11, 15), null, new BigDecimal("1500000"), null, "IN_PROGRESS"));
      triggers.add(buildTrigger(rid, projectId, "SEIAA renewal pending > 60 days",
          RiskTrigger.TriggerType.MILESTONE_MISSED, 60.0, 75.0, true, RiskTrigger.EscalationLevel.RED, "PROJECT_MANAGER"));
    }

    // R-003 Bitumen supply
    if (riskByCode.containsKey("R-003")) {
      UUID rid = riskByCode.get("R-003").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Lock orders with HPCL Mathura; MoU for buffer stock of 500 MT; transport route survey completed.",
          null, LocalDate.of(2025, 7, 1), null, new BigDecimal("800000"), null, "PLANNED"));
      triggers.add(buildTrigger(rid, projectId, "Bitumen price variance > 8% MoM",
          RiskTrigger.TriggerType.COST_OVERRUN, 8.0, 9.5, true, RiskTrigger.EscalationLevel.AMBER, "RESOURCE_MANAGER"));
    }

    // R-004 Utility shifting
    if (riskByCode.containsKey("R-004")) {
      UUID rid = riskByCode.get("R-004").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Joint inspection with PWD; signed shifting plan; VO-002 under review with rate recast.",
          null, LocalDate.of(2025, 10, 1), null, new BigDecimal("1200000"), null, "IN_PROGRESS"));
      triggers.add(buildTrigger(rid, projectId, "Utility conflicts > 3 per km unresolved",
          RiskTrigger.TriggerType.SCHEDULE_DELAY, 3.0, 4.0, true, RiskTrigger.EscalationLevel.AMBER, "PROJECT_MANAGER"));
    }

    // R-005 Labour shortage
    if (riskByCode.containsKey("R-005")) {
      UUID rid = riskByCode.get("R-005").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Labour camp welfare upgrade; staggered leave plan; local hiring drive in Alwar district.",
          null, LocalDate.of(2025, 9, 1), null, new BigDecimal("600000"), null, "PLANNED"));
      triggers.add(buildTrigger(rid, projectId, "Labour attendance < 75% for 3 consecutive days",
          RiskTrigger.TriggerType.MILESTONE_MISSED, 75.0, 72.0, true, RiskTrigger.EscalationLevel.AMBER, "RESOURCE_MANAGER"));
    }

    // R-006 Forex
    if (riskByCode.containsKey("R-006")) {
      UUID rid = riskByCode.get("R-006").getId();
      responses.add(buildResponse(rid, RiskResponseType.ACCEPT,
          "Low exposure (< 2 Cr); monitor INR/USD via monthly treasury review; no hedge required.",
          null, LocalDate.of(2025, 10, 10), null, new BigDecimal("50000"), null, "PLANNED"));
      triggers.add(buildTrigger(rid, projectId, "INR/USD movement > 3% from base rate",
          RiskTrigger.TriggerType.COST_OVERRUN, 3.0, 2.1, false, RiskTrigger.EscalationLevel.GREEN, "FINANCE"));
    }

    // R-007 Monsoon (closed)
    if (riskByCode.containsKey("R-007")) {
      UUID rid = riskByCode.get("R-007").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "Front-loaded earthwork pre-monsoon; protective sheeting; crashed crew deployment Aug-Sep.",
          null, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 9, 30), new BigDecimal("3000000"), new BigDecimal("2800000"), "COMPLETED"));
    }

    // R-008 Soft strata (closed)
    if (riskByCode.containsKey("R-008")) {
      UUID rid = riskByCode.get("R-008").getId();
      responses.add(buildResponse(rid, RiskResponseType.MITIGATE,
          "IIT-Roorkee peer review; deeper pile cap design; VO-001 approved; 12 additional BCS piles.",
          null, LocalDate.of(2025, 4, 10), LocalDate.of(2025, 10, 15), new BigDecimal("5000000"), new BigDecimal("4800000"), "COMPLETED"));
    }

    riskResponseRepository.saveAll(responses);
    riskTriggerRepository.saveAll(triggers);
    log.info("[NH-48-SUPP] seeded {} risk responses and {} triggers", responses.size(), triggers.size());
  }

  private RiskResponse buildResponse(UUID riskId, RiskResponseType type, String description,
                                     UUID responsibleId, LocalDate plannedDate, LocalDate actualDate,
                                     BigDecimal estimatedCost, BigDecimal actualCost, String status) {
    RiskResponse r = new RiskResponse();
    r.setRiskId(riskId);
    r.setResponseType(type);
    r.setDescription(description);
    r.setResponsibleId(responsibleId);
    r.setPlannedDate(plannedDate);
    r.setActualDate(actualDate);
    r.setEstimatedCost(estimatedCost);
    r.setActualCost(actualCost);
    r.setStatus(status);
    return r;
  }

  private RiskTrigger buildTrigger(UUID riskId, UUID projectId, String condition,
                                   RiskTrigger.TriggerType type, double threshold, Double current,
                                   boolean triggered, RiskTrigger.EscalationLevel level, String roles) {
    RiskTrigger t = new RiskTrigger();
    t.setRiskId(riskId);
    t.setProjectId(projectId);
    t.setTriggerCondition(condition);
    t.setTriggerType(type);
    t.setThresholdValue(threshold);
    t.setCurrentValue(current);
    t.setIsTriggered(triggered);
    t.setTriggeredAt(triggered ? Instant.now() : null);
    t.setEscalationLevel(level);
    t.setNotifyRoles(roles);
    return t;
  }

  // ───────────────────── Schedule Scenarios ─────────────────────
  private void seedScheduleScenarios(UUID projectId) {
    if (!scheduleScenarioRepository.findByProjectId(projectId).isEmpty()) {
      log.info("[NH-48-SUPP] schedule scenarios already seeded — skipping");
      return;
    }

    ScheduleScenario baseline = ScheduleScenario.builder()
        .projectId(projectId)
        .scenarioName("NH-48 Baseline — Apr 2025")
        .description("Original baseline schedule with 6-day work week and standard productivity norms.")
        .scenarioType(ScenarioType.BASELINE)
        .baseScheduleResultId(null)
        .projectDuration(720.0)
        .criticalPathLength(720.0)
        .totalCost(new BigDecimal("4850000000.00"))
        .modifiedActivities("None")
        .status(ScenarioStatus.CALCULATED)
        .createdAt(Instant.parse("2025-01-15T00:00:00Z"))
        .build();
    scheduleScenarioRepository.save(baseline);

    ScheduleScenario crash = ScheduleScenario.builder()
        .projectId(projectId)
        .scenarioName("NH-48 Crash — Recover Monsoon Delay")
        .description("Crash scenario with double-shift paving and additional crew to recover 22-day monsoon slip.")
        .scenarioType(ScenarioType.CRASH)
        .baseScheduleResultId(baseline.getId())
        .projectDuration(698.0)
        .criticalPathLength(698.0)
        .totalCost(new BigDecimal("4925000000.00"))
        .modifiedActivities("ACT-3.1,ACT-3.2,ACT-5.1")
        .status(ScenarioStatus.CALCULATED)
        .createdAt(Instant.parse("2025-10-01T00:00:00Z"))
        .build();
    scheduleScenarioRepository.save(crash);

    ScheduleScenario whatIf = ScheduleScenario.builder()
        .projectId(projectId)
        .scenarioName("NH-48 What-If — VO-002 Approved")
        .description("Impact analysis if VO-002 (utility shifting) is approved with 8-day schedule impact.")
        .scenarioType(ScenarioType.WHAT_IF)
        .baseScheduleResultId(baseline.getId())
        .projectDuration(728.0)
        .criticalPathLength(728.0)
        .totalCost(new BigDecimal("4882000000.00"))
        .modifiedActivities("ACT-5.1,ACT-5.2")
        .status(ScenarioStatus.DRAFT)
        .createdAt(Instant.parse("2026-03-01T00:00:00Z"))
        .build();
    scheduleScenarioRepository.save(whatIf);

    log.info("[NH-48-SUPP] seeded {} schedule scenarios", 3);

    // Schedule Health Index
    ScheduleHealthIndex health = ScheduleHealthIndex.builder()
        .scheduleResultId(baseline.getId())
        .projectId(projectId)
        .totalActivities(19)
        .criticalActivities(10)
        .nearCriticalActivities(3)
        .totalFloatAverage(8.5)
        .healthScore(72.5)
        .floatDistribution("0:10,1-5:3,6-10:4,11-20:2,>20:0")
        .riskLevel(RiskLevel.MEDIUM)
        .build();
    scheduleHealthIndexRepository.save(health);
    log.info("[NH-48-SUPP] seeded schedule health index");
  }

  // ───────────────────── Cost Accounts ──────────────────────────
  private void seedCostAccounts() {
    if (costAccountRepository.count() > 0) {
      log.info("[NH-48-SUPP] cost accounts already seeded — skipping");
      return;
    }

    List<CostAccount> accounts = new ArrayList<>();
    CostAccount direct = new CostAccount();
    direct.setCode("CA-DIRECT");
    direct.setName("Direct Works");
    direct.setDescription("Earthwork, pavement, structures and road furniture");
    direct.setSortOrder(1);
    accounts.add(direct);
    costAccountRepository.save(direct);

    CostAccount dirEw = new CostAccount();
    dirEw.setCode("CA-DIR-EW");
    dirEw.setName("Earthwork");
    dirEw.setDescription("Excavation, embankment and compaction");
    dirEw.setParentId(direct.getId());
    dirEw.setSortOrder(10);
    accounts.add(dirEw);

    CostAccount dirPv = new CostAccount();
    dirPv.setCode("CA-DIR-PV");
    dirPv.setName("Pavement");
    dirPv.setDescription("GSB, WMM, DBM, BC layers");
    dirPv.setParentId(direct.getId());
    dirPv.setSortOrder(20);
    accounts.add(dirPv);

    CostAccount dirSt = new CostAccount();
    dirSt.setCode("CA-DIR-ST");
    dirSt.setName("Structures");
    dirSt.setDescription("Bridges, culverts and retaining walls");
    dirSt.setParentId(direct.getId());
    dirSt.setSortOrder(30);
    accounts.add(dirSt);

    CostAccount dirRf = new CostAccount();
    dirRf.setCode("CA-DIR-RF");
    dirRf.setName("Road Furniture");
    dirRf.setDescription("Signage, marking, kerbs and guardrails");
    dirRf.setParentId(direct.getId());
    dirRf.setSortOrder(40);
    accounts.add(dirRf);

    CostAccount dirDr = new CostAccount();
    dirDr.setCode("CA-DIR-DR");
    dirDr.setName("Drainage");
    dirDr.setDescription("Catch water drains, cross drains");
    dirDr.setParentId(direct.getId());
    dirDr.setSortOrder(50);
    accounts.add(dirDr);

    CostAccount indirect = new CostAccount();
    indirect.setCode("CA-INDIRECT");
    indirect.setName("Indirect Costs");
    indirect.setDescription("Overheads, site establishment and miscellaneous");
    indirect.setSortOrder(2);
    accounts.add(indirect);
    costAccountRepository.save(indirect);

    CostAccount indSite = new CostAccount();
    indSite.setCode("CA-IND-SITE");
    indSite.setName("Site Establishment");
    indSite.setDescription("Camp, site office, utilities");
    indSite.setParentId(indirect.getId());
    indSite.setSortOrder(60);
    accounts.add(indSite);

    CostAccount indEquip = new CostAccount();
    indEquip.setCode("CA-IND-EQUIP");
    indEquip.setName("Equipment Hire");
    indEquip.setDescription("Non-direct equipment and tools");
    indEquip.setParentId(indirect.getId());
    indEquip.setSortOrder(70);
    accounts.add(indEquip);

    CostAccount indSafety = new CostAccount();
    indSafety.setCode("CA-IND-SAFETY");
    indSafety.setName("Safety & Environment");
    indSafety.setDescription("PPE, signage, environmental compliance");
    indSafety.setParentId(indirect.getId());
    indSafety.setSortOrder(80);
    accounts.add(indSafety);

    costAccountRepository.saveAll(accounts.subList(1, accounts.size()));
    log.info("[NH-48-SUPP] seeded {} cost accounts", accounts.size());
  }
}
