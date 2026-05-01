package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractStatus;
import com.bipros.contract.domain.model.ContractType;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.PerformanceBondRepository;
import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.SatelliteGate;
import com.bipros.cost.domain.repository.RaBillRepository;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.model.DrawingStatus;
import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.model.RfiStatus;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DrawingRegisterRepository;
import com.bipros.document.domain.repository.RfiRegisterRepository;
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
import com.bipros.api.config.seeder.util.SeederResourceFactory;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.model.ResourceMaterialDetails;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
import com.bipros.resource.domain.repository.LabourReturnRepository;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.bipros.resource.domain.repository.ResourceDailyLogRepository;
import com.bipros.resource.domain.repository.ResourceEquipmentDetailsRepository;
import com.bipros.resource.domain.repository.ResourceMaterialDetailsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import com.bipros.risk.domain.model.RiskType;
import com.bipros.risk.domain.repository.RiskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS Excel master-data loader — one-shot replacement seeder that ingests the
 * canonical {@code seed-data/ic-pms-sample.xlsx} workbook and populates six tables
 * with the exact values from the Excel sheets (M3-M8).
 *
 * <p>Runs at @Order(109) — AFTER the invented-data {@code IcpmsPhase[B-E]Seeder}s
 * (102-105) but BEFORE the resource log seeders ({@code IcpmsEquipmentLogSeeder}
 * at 110, Labour/Material at 111-112) and the drawing/rfi/transmittal seeders
 * (113-115). The loader therefore deletes the invented rows Phase B-E just wrote
 * plus every FK-dependent row (perf bonds, resource daily logs, drawing / rfi
 * registers, progress snapshots) and re-inserts from the Excel workbook. The
 * subsequent log + register seeders see the Excel-sourced rows and either seed
 * against them (equipment / labour / material) or skip via their own
 * {@code count() > 0} sentinels (drawing / rfi registers).
 *
 * <p>The widened {@code count() > 0} sentinels added to Phase B / C / D and the
 * split sentinel added to Phase E are still there as belt-and-braces — they keep
 * those seeders idempotent if the Excel loader has already run on a prior boot
 * where ddl-auto preserves data.
 *
 * <p>Sheets owned:
 * <ul>
 *   <li>{@code M5_Contract_Register} → {@code contract.contracts}</li>
 *   <li>{@code M4_Cost_RA_Bills} → {@code cost.ra_bills}</li>
 *   <li>{@code M6_Document_Register} → {@code document.documents} + drawing/rfi registers</li>
 *   <li>{@code M8_Resource_Register} → {@code resource.resources}</li>
 *   <li>{@code M3_Satellite_Monitoring} → {@code gis.satellite_images} + progress snapshots</li>
 *   <li>{@code M7_Risk_Register} → {@code risk.risks}</li>
 * </ul>
 *
 * <p>Sheets explicitly NOT touched: M1_WBS_GIS, M2_Schedule_Activities, M9_Reports_Analytics,
 * MasterData_OrgUsers, MasterData_Calendars.
 *
 * <p>Transaction discipline: the whole {@link #run(String...)} is @Transactional — if any
 * sheet fails, every insert rolls back. Deletes are flushed before inserts.
 */
@Slf4j
@Component
@Profile("dev")
@Order(109)
@RequiredArgsConstructor
public class ExcelMasterDataLoader implements CommandLineRunner {

    private static final String EXCEL_PATH = "seed-data/ic-pms-sample.xlsx";
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ENGLISH);

    // Dependencies
    private final ProjectRepository projectRepository;
    private final OrganisationRepository organisationRepository;
    private final ContractRepository contractRepository;
    private final PerformanceBondRepository performanceBondRepository;
    private final RaBillRepository raBillRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFolderRepository folderRepository;
    private final DrawingRegisterRepository drawingRegisterRepository;
    private final RfiRegisterRepository rfiRegisterRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceEquipmentDetailsRepository resourceEquipmentDetailsRepository;
    private final ResourceMaterialDetailsRepository resourceMaterialDetailsRepository;
    private final ResourceDailyLogRepository resourceDailyLogRepository;
    private final SeederResourceFactory resourceFactory;
    private final EquipmentLogRepository equipmentLogRepository;
    private final LabourReturnRepository labourReturnRepository;
    private final MaterialReconciliationRepository materialReconciliationRepository;
    private final SatelliteImageRepository satelliteImageRepository;
    private final ConstructionProgressSnapshotRepository progressSnapshotRepository;
    private final RiskRepository riskRepository;
    private final LegacyRiskCategoryLookup legacyCategoryLookup;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        // Idempotency sentinel: detect a prior successful Excel load by the presence of
        // the Excel-specific contract number DMIC/P01/LOA/2024-012 which no other seeder emits.
        if (contractRepository.findByContractNumber("DMIC/P01/LOA/2024-012").isPresent()) {
            log.info("[Excel] master-data already loaded (sentinel DMIC/P01/LOA/2024-012 present), skipping");
            return;
        }

        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[Excel] DMIC-PROG project not found — Phase A seeder must run first");
            return;
        }
        UUID projectId = programme.getId();

        ClassPathResource resource = new ClassPathResource(EXCEL_PATH);
        if (!resource.exists()) {
            log.warn("[Excel] workbook {} not found on classpath — skipping", EXCEL_PATH);
            return;
        }

        log.info("[Excel] loading IC-PMS master data from {}…", EXCEL_PATH);

        try (InputStream is = resource.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            // --- Delete cascades first: anything FK-dependent on the six tables. ---
            clearDependents();

            Map<String, UUID> organisationByKeyword = buildOrganisationKeywordIndex();

            // Contracts come first — RA bills join to contracts by LOA number.
            Map<String, UUID> contractByLoa = loadContracts(wb, projectId, organisationByKeyword);

            // Satellite scenes must land before RA bills so SatelliteGateService can read snapshots.
            loadSatelliteScenes(wb, projectId);

            loadRaBills(wb, projectId, contractByLoa);

            // Folders must exist before documents; we reuse any from Phase D or create fresh ones.
            Map<DocumentType, UUID> folderByType = ensureDocumentFolders(projectId);
            loadDocuments(wb, projectId, folderByType);

            loadResources(wb, organisationByKeyword);

            loadRisks(wb, projectId);

            entityManager.flush();

            log.info("[Excel] master data load complete");
        } catch (IOException e) {
            throw new IllegalStateException("[Excel] failed to read workbook " + EXCEL_PATH, e);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /** Delete FK-dependent rows before truncating the six owner tables. */
    private void clearDependents() {
        // Resource sub-tables (FK → resource.resources)
        materialReconciliationRepository.deleteAllInBatch();
        labourReturnRepository.deleteAllInBatch();
        equipmentLogRepository.deleteAllInBatch();
        resourceDailyLogRepository.deleteAllInBatch();
        // Contract sub-tables (FK → contract.contracts)
        performanceBondRepository.deleteAllInBatch();
        // Document sub-tables (FK → document.documents via documentId)
        drawingRegisterRepository.deleteAllInBatch();
        rfiRegisterRepository.deleteAllInBatch();
        // GIS snapshots reference satellite_images.id
        progressSnapshotRepository.deleteAllInBatch();
        // Now the owner tables themselves
        raBillRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
        resourceRepository.deleteAllInBatch();
        satelliteImageRepository.deleteAllInBatch();
        riskRepository.deleteAllInBatch();
        entityManager.flush();
        log.info("[Excel] cleared owner tables + FK-dependent rows");
    }

    private Map<String, UUID> buildOrganisationKeywordIndex() {
        Map<String, UUID> map = new HashMap<>();
        for (Organisation o : organisationRepository.findAll()) {
            map.put(o.getCode(), o.getId());
            // Keyword-ish index: short name lowercase, name tokens
            if (o.getShortName() != null) {
                map.putIfAbsent(o.getShortName().toLowerCase(Locale.ROOT), o.getId());
            }
            if (o.getName() != null) {
                map.putIfAbsent(o.getName().toLowerCase(Locale.ROOT), o.getId());
            }
        }
        return map;
    }

    /**
     * Resolve a contractor organisation UUID from the Excel "Contractor Name" free-text.
     * Matches by substring against a keyword → orgCode table.
     */
    private UUID resolveContractorOrg(String text, Map<String, UUID> index) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        // Keyword → existing org code map (falls back to substring scan)
        String[][] keyToCode = {
            {"l&t construction", "LNT-IDPL"},
            {"larsen & toubro", "LNT-IDPL"},
            {"l&t power", "LNT-IDPL"},
            {"l&t engg", "LNT-IDPL"},
            {"tata projects", "TATA-PROJ"},
            {"tata metaliks", "TATA-PROJ"},
            {"afcons", "AFCONS"},
            {"hcc", "HCC"},
            {"hindustan construction", "HCC"},
            {"dilip buildcon", "DILIP-BUILDCON"},
            {"aecom", "AECOM-TYPSA"},
            {"egis", "EGIS-PMC"},
            {"mott", "MOTT-MAC"},
            {"spml", "DILIP-BUILDCON"},         // No SPML org seeded; map to DILIP-BUILDCON as closest EPC
            {"sterlite", "LNT-IDPL"},           // No Sterlite org; bucket under LNT (ICT packages)
            {"va tech wabag", "TATA-PROJ"},     // No wabag org; bucket under TATA
            {"nbcc", "LNT-IDPL"},                // No NBCC org; bucket under LNT
            {"shapoorji pallonji", "HCC"},       // No SP org; bucket under HCC
            {"survey of india", "AECOM-TYPSA"}   // Survey contractor under PMC
        };
        for (String[] kv : keyToCode) {
            if (lower.contains(kv[0])) {
                UUID id = index.get(kv[1]);
                if (id != null) return id;
            }
        }
        // Last-resort: literal org code lookup
        return index.getOrDefault(text, null);
    }

    /** Convert Excel cell → string, handling BLANK / NUMERIC / FORMULA / BOOLEAN. */
    private String stringValue(Cell c) {
        if (c == null) return null;
        CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (type == CellType.BLANK || type == CellType.ERROR) return null;
        String v = FORMATTER.formatCellValue(c).trim();
        // Excel em-dash placeholder "—" → null
        if (v.isEmpty() || "—".equals(v) || "-".equals(v)) return null;
        return v;
    }

    /** Convert Excel cell → LocalDate for numeric-date cells OR string-date (multiple formats). */
    private LocalDate dateValue(Cell c) {
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                Date d = c.getDateCellValue();
                if (d == null) return null;
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            String s = stringValue(c);
            if (s == null) return null;
            return parseDate(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || "—".equals(s) || "-".equals(s)) return null;
        // Excel uses "15-Jun-2024" style pervasively
        String[] patterns = {"dd-MMM-yyyy", "d-MMM-yyyy", "dd-MMM-yy", "dd/MM/yyyy", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(s,
                    java.time.format.DateTimeFormatter.ofPattern(p, Locale.ENGLISH));
            } catch (Exception ignore) { /* try next */ }
        }
        return null;
    }

    private BigDecimal bdValue(Cell c) {
        String s = stringValue(c);
        if (s == null) return null;
        // Strip commas and any stray currency markers
        s = s.replace(",", "").replace("₹", "").trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double doubleValue(Cell c) {
        BigDecimal v = bdValue(c);
        return v == null ? null : v.doubleValue();
    }

    private Integer intValue(Cell c) {
        BigDecimal v = bdValue(c);
        return v == null ? null : v.intValue();
    }

    // =========================================================================
    // M5 Contracts
    // =========================================================================
    private Map<String, UUID> loadContracts(Workbook wb, UUID projectId, Map<String, UUID> orgIndex) {
        Map<String, UUID> contractByLoa = new HashMap<>();
        Sheet sheet = wb.getSheet("M5_Contract_Register");
        if (sheet == null) {
            log.warn("[Excel] sheet M5_Contract_Register missing — skipping contracts");
            return contractByLoa;
        }
        int seeded = 0;
        // Data rows start at row index 3 (row 1 title, row 2 blank, row 3 header, row 4+ data).
        // Apache POI is 0-based, so row index 3 is "r4" in the Excel dump.
        int lastRow = sheet.getLastRowNum();
        for (int r = 3; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String loaNo = stringValue(row.getCell(0));
            if (loaNo == null) continue;
            String packageCode = stringValue(row.getCell(1));
            String packageDesc = stringValue(row.getCell(2));
            String contractorName = stringValue(row.getCell(3));
            String contractTypeText = stringValue(row.getCell(4));
            BigDecimal loaValue = bdValue(row.getCell(5));
            LocalDate loaDate = dateValue(row.getCell(6));
            LocalDate contractStart = dateValue(row.getCell(7));
            LocalDate contractEnd = dateValue(row.getCell(8));
            LocalDate actualCompletion = dateValue(row.getCell(9));
            BigDecimal spi = bdValue(row.getCell(10));
            BigDecimal cpi = bdValue(row.getCell(11));
            BigDecimal physicalProgress = bdValue(row.getCell(12));
            BigDecimal cumRaBills = bdValue(row.getCell(13));
            Integer voNos = intValue(row.getCell(14));
            BigDecimal voValue = bdValue(row.getCell(15));
            String statusText = stringValue(row.getCell(16));
            BigDecimal perfScore = bdValue(row.getCell(17));
            LocalDate bgExpiry = dateValue(row.getCell(18));

            Contract c = new Contract();
            c.setProjectId(projectId);
            c.setContractNumber(loaNo);
            c.setLoaNumber(loaNo);
            c.setContractorName(contractorName != null ? contractorName : "Unknown");
            UUID contractorOrgId = resolveContractorOrg(contractorName, orgIndex);
            if (contractorOrgId != null) {
                organisationRepository.findById(contractorOrgId)
                    .ifPresent(o -> c.setContractorCode(o.getCode()));
            }
            c.setWbsPackageCode(packageCode);
            c.setPackageDescription(packageDesc);
            c.setContractType(mapContractType(contractTypeText));
            c.setContractValue(loaValue);
            c.setLoaDate(loaDate);
            c.setStartDate(contractStart);
            c.setCompletionDate(contractEnd);
            c.setActualCompletionDate(actualCompletion);
            c.setSpi(spi);
            c.setCpi(cpi);
            c.setPhysicalProgressAi(physicalProgress);
            c.setCumulativeRaBillsCrores(cumRaBills);
            c.setVoNumbersIssued(voNos);
            c.setVoValueCrores(voValue);
            c.setStatus(mapContractStatus(statusText));
            c.setPerformanceScore(perfScore);
            c.setBgExpiry(bgExpiry);
            c.setDlpMonths(12);
            c.setLdRate(0.05);
            Contract saved = contractRepository.save(c);
            contractByLoa.put(loaNo, saved.getId());
            seeded++;
        }
        entityManager.flush();
        log.info("[Excel] loaded {} contracts from M5_Contract_Register", seeded);
        return contractByLoa;
    }

    private ContractType mapContractType(String text) {
        if (text == null) return ContractType.EPC_LUMP_SUM_FIDIC_YELLOW;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("percentage") || t.contains("pmc")) return ContractType.PERCENTAGE_BASED_PMC;
        if (t.contains("item rate") || t.contains("fidic red")) return ContractType.ITEM_RATE_FIDIC_RED;
        if (t.contains("silver")) return ContractType.EPC_LUMP_SUM_FIDIC_SILVER;
        if (t.contains("yellow")) return ContractType.EPC_LUMP_SUM_FIDIC_YELLOW;
        if (t.contains("lump sum") && t.contains("unit")) return ContractType.LUMP_SUM_UNIT_RATE;
        if (t.contains("lump sum")) return ContractType.EPC_LUMP_SUM_FIDIC_YELLOW;
        return ContractType.EPC_LUMP_SUM_FIDIC_YELLOW;
    }

    private ContractStatus mapContractStatus(String text) {
        if (text == null) return ContractStatus.ACTIVE;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("at risk")) return ContractStatus.ACTIVE_AT_RISK;
        if (t.contains("delayed") && t.contains("active")) return ContractStatus.ACTIVE_DELAYED;
        if (t.equals("delayed")) return ContractStatus.DELAYED;
        if (t.contains("mobilisation") || t.contains("mobilization")) return ContractStatus.MOBILISATION;
        if (t.contains("completed")) return ContractStatus.COMPLETED;
        if (t.contains("suspend")) return ContractStatus.SUSPENDED;
        if (t.contains("terminat")) return ContractStatus.TERMINATED;
        if (t.contains("dlp")) return ContractStatus.DLP;
        if (t.contains("draft")) return ContractStatus.DRAFT;
        return ContractStatus.ACTIVE;
    }

    // =========================================================================
    // M4 RA Bills
    // =========================================================================
    private void loadRaBills(Workbook wb, UUID projectId, Map<String, UUID> contractByLoa) {
        Sheet sheet = wb.getSheet("M4_Cost_RA_Bills");
        if (sheet == null) {
            log.warn("[Excel] sheet M4_Cost_RA_Bills missing — skipping bills");
            return;
        }
        int seeded = 0;
        for (int r = 3; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String billNo = stringValue(row.getCell(0));
            if (billNo == null) continue;
            String loaNo = stringValue(row.getCell(1));
            String wbsPackage = stringValue(row.getCell(2));
            // Col 3: Contractor (denorm, not stored)
            LocalDate billDate = dateValue(row.getCell(4));
            LocalDate periodFrom = dateValue(row.getCell(5));
            LocalDate periodTo = dateValue(row.getCell(6));
            BigDecimal gross = bdValue(row.getCell(7));
            BigDecimal mobAdvance = bdValue(row.getCell(8));
            BigDecimal retention = bdValue(row.getCell(9));
            BigDecimal tds = bdValue(row.getCell(10));
            BigDecimal gst = bdValue(row.getCell(11));
            BigDecimal netPayable = bdValue(row.getCell(12));
            BigDecimal aiPct = bdValue(row.getCell(13));
            BigDecimal claimPct = bdValue(row.getCell(14));
            String gateText = stringValue(row.getCell(15));
            String approvalText = stringValue(row.getCell(16));
            String pfmsRef = stringValue(row.getCell(17));
            LocalDate paymentDate = dateValue(row.getCell(18));

            RaBill bill = new RaBill();
            bill.setProjectId(projectId);
            bill.setBillNumber(billNo);
            bill.setWbsPackageCode(wbsPackage);
            if (loaNo != null && contractByLoa.containsKey(loaNo)) {
                bill.setContractId(contractByLoa.get(loaNo));
            }
            bill.setBillPeriodFrom(periodFrom != null ? periodFrom :
                (periodTo != null ? periodTo : (billDate != null ? billDate : LocalDate.now())));
            bill.setBillPeriodTo(periodTo != null ? periodTo :
                (periodFrom != null ? periodFrom : (billDate != null ? billDate : LocalDate.now())));
            bill.setGrossAmount(gross != null ? gross : BigDecimal.ZERO);
            bill.setMobAdvanceRecovery(mobAdvance);
            bill.setRetention5Pct(retention);
            bill.setTds2Pct(tds);
            bill.setGst18Pct(gst);
            BigDecimal deductions = BigDecimal.ZERO;
            if (mobAdvance != null) deductions = deductions.add(mobAdvance);
            if (retention != null) deductions = deductions.add(retention);
            if (tds != null) deductions = deductions.add(tds);
            if (gst != null) deductions = deductions.add(gst);
            bill.setDeductions(deductions);
            bill.setNetAmount(netPayable != null ? netPayable : bill.getGrossAmount().subtract(deductions));
            bill.setAiSatellitePercent(aiPct);
            bill.setContractorClaimedPercent(claimPct);
            bill.setSatelliteGate(mapSatelliteGate(gateText));
            if (aiPct != null && claimPct != null) {
                bill.setSatelliteGateVariance(claimPct.subtract(aiPct).abs().setScale(2, RoundingMode.HALF_UP));
            }
            bill.setPfmsDpaRef(pfmsRef);
            bill.setPaymentDate(paymentDate);
            bill.setSubmittedDate(billDate);
            bill.setStatus(mapRaBillStatus(approvalText));
            if (paymentDate != null) {
                bill.setPaidDate(paymentDate);
            }
            raBillRepository.save(bill);
            seeded++;
        }
        entityManager.flush();
        log.info("[Excel] loaded {} RA bills from M4_Cost_RA_Bills", seeded);
    }

    private SatelliteGate mapSatelliteGate(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("pass")) return SatelliteGate.PASS;
        if (t.startsWith("red")) return SatelliteGate.RED_VARIANCE;
        if (t.contains("dispute")) return SatelliteGate.HOLD_SATELLITE_DISPUTE;
        if (t.contains("hold") || t.contains("variance")) return SatelliteGate.HOLD_VARIANCE;
        return null;
    }

    private RaBill.RaBillStatus mapRaBillStatus(String text) {
        if (text == null) return RaBill.RaBillStatus.SUBMITTED;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("paid") && t.contains("override")) return RaBill.RaBillStatus.PAID_PMC_OVERRIDE;
        if (t.contains("paid")) return RaBill.RaBillStatus.PAID;
        if (t.contains("hold") && t.contains("satellite")) return RaBill.RaBillStatus.HOLD_SATELLITE_DISPUTE;
        if (t.contains("pmc review") || t.contains("pending")) return RaBill.RaBillStatus.PMC_REVIEW_PENDING;
        if (t.contains("reject")) return RaBill.RaBillStatus.REJECTED;
        if (t.contains("approved")) return RaBill.RaBillStatus.APPROVED;
        if (t.contains("certified")) return RaBill.RaBillStatus.CERTIFIED;
        if (t.contains("draft")) return RaBill.RaBillStatus.DRAFT;
        return RaBill.RaBillStatus.SUBMITTED;
    }

    // =========================================================================
    // M3 Satellite scenes + progress snapshots
    // =========================================================================
    private void loadSatelliteScenes(Workbook wb, UUID projectId) {
        Sheet sheet = wb.getSheet("M3_Satellite_Monitoring");
        if (sheet == null) {
            log.warn("[Excel] sheet M3_Satellite_Monitoring missing — skipping scenes");
            return;
        }
        int seeded = 0;
        for (int r = 3; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String sceneId = stringValue(row.getCell(0));
            if (sceneId == null) continue;
            String wbsNodeText = stringValue(row.getCell(1));
            String sourceText = stringValue(row.getCell(2));
            LocalDate captureDate = dateValue(row.getCell(3));
            Double cloudCover = doubleValue(row.getCell(4));
            String resolutionText = stringValue(row.getCell(5));
            Double aiProgress = doubleValue(row.getCell(6));
            Double claimedPct = doubleValue(row.getCell(7));
            Double variance = doubleValue(row.getCell(8));
            String alertText = stringValue(row.getCell(9));
            Double cvi = doubleValue(row.getCell(10));
            Double edi = doubleValue(row.getCell(11));
            Double ndviChange = doubleValue(row.getCell(12));
            String statusText = stringValue(row.getCell(13));

            SatelliteImage img = new SatelliteImage();
            img.setProjectId(projectId);
            img.setSceneId(sceneId);
            img.setImageName(sceneId);
            img.setDescription(wbsNodeText);
            img.setCaptureDate(captureDate != null ? captureDate : LocalDate.now());
            img.setSource(mapSatelliteSource(sourceText));
            img.setResolution(resolutionText != null ? resolutionText + "m" : "0.5m");
            img.setCloudCoverPercent(cloudCover);
            img.setFilePath("metadata-only/" + sceneId + ".tif");
            img.setFileSize(524288000L);
            img.setMimeType("image/tiff");
            img.setStatus(mapSatelliteImageStatus(statusText));
            SatelliteImage savedImg = satelliteImageRepository.save(img);

            // Construction progress snapshot
            ConstructionProgressSnapshot snap = new ConstructionProgressSnapshot();
            snap.setProjectId(projectId);
            snap.setSatelliteImageId(savedImg.getId());
            snap.setCaptureDate(img.getCaptureDate());
            snap.setWbsPackageCode(deriveSatelliteWbsCode(sceneId, wbsNodeText));
            snap.setAiProgressPercent(aiProgress);
            snap.setDerivedProgressPercent(aiProgress);
            snap.setContractorClaimedPercent(claimedPct);
            snap.setVariancePercent(variance);
            snap.setCvi(cvi);
            snap.setEdi(edi);
            snap.setNdviChange(ndviChange);
            snap.setAlertFlag(mapAlertFlag(alertText));
            snap.setAnalysisMethod(ProgressAnalysisMethod.AI_SEGMENTATION);
            snap.setRemarks("Loaded from Excel M3_Satellite_Monitoring");
            progressSnapshotRepository.save(snap);
            seeded++;
        }
        entityManager.flush();
        log.info("[Excel] loaded {} satellite scenes + progress snapshots from M3_Satellite_Monitoring", seeded);
    }

    /**
     * Derive the WBS package code from scene ID + WBS Node free-text. Scene IDs take
     * the form SCN-N03-YYMMDD, SCN-N03-P01-YYMMDD, or SCN-N03-ALERT-nn; the 2nd
     * segment gives the node number (N03, N04, etc.). Fall back to programme root.
     */
    private String deriveSatelliteWbsCode(String sceneId, String wbsNodeText) {
        if (sceneId == null) return null;
        String[] parts = sceneId.split("-");
        if (parts.length >= 2 && parts[1].matches("N\\d+")) {
            return "DMIC-" + parts[1];
        }
        return "DMIC";
    }

    private SatelliteImageSource mapSatelliteSource(String text) {
        if (text == null) return SatelliteImageSource.ISRO_CARTOSAT;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("isro") || t.contains("cartosat")) return SatelliteImageSource.ISRO_CARTOSAT;
        if (t.contains("planet")) return SatelliteImageSource.PLANET_LABS;
        if (t.contains("maxar")) return SatelliteImageSource.MAXAR;
        if (t.contains("airbus") || t.contains("pleiade") || t.contains("pléiade")) return SatelliteImageSource.AIRBUS;
        if (t.contains("drone")) return SatelliteImageSource.DRONE;
        return SatelliteImageSource.MANUAL_UPLOAD;
    }

    private SatelliteImageStatus mapSatelliteImageStatus(String text) {
        if (text == null) return SatelliteImageStatus.READY;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("process") && t.contains("ed")) return SatelliteImageStatus.READY;
        if (t.contains("processing")) return SatelliteImageStatus.PROCESSING;
        if (t.contains("alert")) return SatelliteImageStatus.READY;
        if (t.contains("fail")) return SatelliteImageStatus.FAILED;
        if (t.contains("upload")) return SatelliteImageStatus.UPLOADED;
        return SatelliteImageStatus.READY;
    }

    private SatelliteAlertFlag mapAlertFlag(String text) {
        if (text == null) return SatelliteAlertFlag.GREEN;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("encroach")) return SatelliteAlertFlag.RED_ENCROACHMENT;
        if (t.contains("red")) return SatelliteAlertFlag.RED_VARIANCE_GT10;
        if (t.contains("idle")) return SatelliteAlertFlag.AMBER_IDLE_ZONE;
        if (t.contains("amber")) return SatelliteAlertFlag.AMBER_VARIANCE_GT5;
        return SatelliteAlertFlag.GREEN;
    }

    // =========================================================================
    // M6 Documents + drawing/rfi registers
    // =========================================================================
    private Map<DocumentType, UUID> ensureDocumentFolders(UUID projectId) {
        Map<DocumentType, UUID> map = new HashMap<>();
        // Reuse folders that already exist on this project (seeded by Phase D if it ran first),
        // else create minimal ones keyed by document type.
        List<DocumentFolder> existing = folderRepository.findByProjectIdOrderBySortOrder(projectId);
        UUID drawings = null, specs = null, rfi = null, minutes = null,
            contracts = null, reports = null, bg = null;
        for (DocumentFolder f : existing) {
            String code = f.getCode();
            if (code == null) continue;
            switch (code) {
                case "DRAWINGS": drawings = f.getId(); break;
                case "SPECS": specs = f.getId(); break;
                case "RFI": rfi = f.getId(); break;
                case "MINUTES": minutes = f.getId(); break;
                case "CONTRACTS": contracts = f.getId(); break;
                case "REPORTS": reports = f.getId(); break;
                case "BG": bg = f.getId(); break;
                default: break;
            }
        }
        drawings = drawings != null ? drawings : createFolder(projectId, "DRAWINGS", "Drawings", DocumentCategory.DRAWING);
        specs = specs != null ? specs : createFolder(projectId, "SPECS", "Specifications", DocumentCategory.SPECIFICATION);
        rfi = rfi != null ? rfi : createFolder(projectId, "RFI", "Requests for Information", DocumentCategory.CORRESPONDENCE);
        minutes = minutes != null ? minutes : createFolder(projectId, "MINUTES", "Meeting Minutes", DocumentCategory.CORRESPONDENCE);
        contracts = contracts != null ? contracts : createFolder(projectId, "CONTRACTS", "LOA & Contracts", DocumentCategory.CONTRACT);
        reports = reports != null ? reports : createFolder(projectId, "REPORTS", "Reports", DocumentCategory.GENERAL);
        bg = bg != null ? bg : createFolder(projectId, "BG", "Bank Guarantees", DocumentCategory.APPROVAL);

        map.put(DocumentType.DRAWING, drawings);
        map.put(DocumentType.SPECIFICATION, specs);
        map.put(DocumentType.RFI, rfi);
        map.put(DocumentType.MINUTES, minutes);
        map.put(DocumentType.CONTRACT_DOCUMENT, contracts);
        map.put(DocumentType.LOA, contracts);
        map.put(DocumentType.REPORT, reports);
        map.put(DocumentType.BANK_GUARANTEE, bg);
        return map;
    }

    private UUID createFolder(UUID projectId, String code, String name, DocumentCategory category) {
        DocumentFolder f = new DocumentFolder();
        f.setProjectId(projectId);
        f.setCode(code);
        f.setName(name);
        f.setCategory(category);
        f.setSortOrder(0);
        return folderRepository.save(f).getId();
    }

    private void loadDocuments(Workbook wb, UUID projectId, Map<DocumentType, UUID> folderByType) {
        Sheet sheet = wb.getSheet("M6_Document_Register");
        if (sheet == null) {
            log.warn("[Excel] sheet M6_Document_Register missing — skipping documents");
            return;
        }
        int seeded = 0, drawingRegs = 0, rfiRegs = 0;
        for (int r = 3; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String docCode = stringValue(row.getCell(0));
            if (docCode == null) continue;
            String title = stringValue(row.getCell(1));
            String typeText = stringValue(row.getCell(2));
            String wbsPackage = stringValue(row.getCell(3));
            String disciplineText = stringValue(row.getCell(4));
            String revision = stringValue(row.getCell(5));
            String statusText = stringValue(row.getCell(6));
            String issuedBy = stringValue(row.getCell(7));
            LocalDate issuedDate = dateValue(row.getCell(8));
            String approvedBy = stringValue(row.getCell(9));
            LocalDate approvedDate = dateValue(row.getCell(10));
            Double fileSizeMb = doubleValue(row.getCell(11));
            String transmittalNo = stringValue(row.getCell(12));

            DocumentType docType = mapDocumentType(typeText, docCode);
            DrawingDiscipline discipline = mapDrawingDiscipline(disciplineText);
            DocumentStatus status = mapDocumentStatus(statusText);

            Document d = new Document();
            d.setProjectId(projectId);
            d.setFolderId(folderByType.getOrDefault(docType, folderByType.get(DocumentType.REPORT)));
            d.setDocumentNumber(docCode);
            d.setTitle(title != null ? title : docCode);
            d.setDocumentType(docType);
            d.setDiscipline(discipline);
            d.setStatus(status);
            d.setWbsPackageCode(wbsPackage);
            d.setIssuedBy(issuedBy);
            d.setIssuedDate(issuedDate);
            d.setApprovedBy(approvedBy);
            d.setApprovedDate(approvedDate);
            d.setTransmittalNumber(transmittalNo);
            d.setCurrentVersion(1);
            d.setFileName(docCode + ".pdf");
            long sizeBytes = fileSizeMb != null ? Math.round(fileSizeMb * 1024.0 * 1024.0) : 1024L;
            if (sizeBytes <= 0) sizeBytes = 1024L;
            d.setFileSize(sizeBytes);
            d.setMimeType("application/pdf");
            d.setFilePath("metadata-only/" + docCode + ".pdf");
            Document saved = documentRepository.save(d);
            seeded++;

            // Cascade: DRAWING doc → drawing_registers row
            if (docType == DocumentType.DRAWING) {
                DrawingRegister dr = new DrawingRegister();
                dr.setProjectId(projectId);
                dr.setDocumentId(saved.getId());
                dr.setDrawingNumber(docCode);
                dr.setTitle(title != null ? title : docCode);
                dr.setDiscipline(discipline != null ? discipline : DrawingDiscipline.CIVIL);
                dr.setRevision(revision != null ? revision : "A");
                dr.setRevisionDate(issuedDate != null ? issuedDate : LocalDate.now());
                dr.setStatus(mapDrawingStatusFromDocStatus(status));
                dr.setPackageCode(wbsPackage != null ? wbsPackage : "DMIC-PROG");
                dr.setScale("1:500");
                drawingRegisterRepository.save(dr);
                drawingRegs++;
            }
            // Cascade: RFI doc → rfi_registers row
            if (docType == DocumentType.RFI) {
                RfiRegister rf = new RfiRegister();
                rf.setProjectId(projectId);
                rf.setRfiNumber(docCode);
                rf.setSubject(title != null ? title : docCode);
                rf.setDescription(title);
                rf.setRaisedBy(issuedBy != null ? issuedBy : "EPC Contractor");
                rf.setAssignedTo(approvedBy != null ? approvedBy : "PMC");
                rf.setRaisedDate(issuedDate != null ? issuedDate : LocalDate.now());
                rf.setDueDate(issuedDate != null ? issuedDate.plusDays(14) : LocalDate.now().plusDays(14));
                if (status == DocumentStatus.CLOSED) {
                    rf.setClosedDate(approvedDate != null ? approvedDate : issuedDate);
                    rf.setStatus(RfiStatus.CLOSED);
                } else {
                    rf.setStatus(RfiStatus.OPEN);
                }
                rf.setPriority(RfiPriority.MEDIUM);
                rfiRegisterRepository.save(rf);
                rfiRegs++;
            }
        }
        entityManager.flush();
        log.info("[Excel] loaded {} documents from M6_Document_Register ({} drawings, {} RFIs cascaded)",
            seeded, drawingRegs, rfiRegs);
    }

    private DocumentType mapDocumentType(String text, String docCode) {
        if (text != null) {
            String t = text.toLowerCase(Locale.ROOT);
            if (t.contains("drawing")) return DocumentType.DRAWING;
            if (t.contains("specification") || t.contains("spec")) return DocumentType.SPECIFICATION;
            if (t.contains("rfi")) return DocumentType.RFI;
            if (t.contains("minute")) return DocumentType.MINUTES;
            if (t.contains("contract document") || t.contains("loa")) {
                // LOA prefix → LOA type; BRD prefix → bank guarantee
                if (docCode != null && docCode.startsWith("BRD")) return DocumentType.BANK_GUARANTEE;
                if (docCode != null && docCode.startsWith("LOA")) return DocumentType.LOA;
                return DocumentType.CONTRACT_DOCUMENT;
            }
            if (t.contains("bank guarantee") || t.contains("bg")) return DocumentType.BANK_GUARANTEE;
            if (t.contains("report") || t.contains("mpr")) return DocumentType.REPORT;
        }
        // Fall back on prefix
        if (docCode != null) {
            if (docCode.startsWith("DRW")) return DocumentType.DRAWING;
            if (docCode.startsWith("SPEC")) return DocumentType.SPECIFICATION;
            if (docCode.startsWith("RFI")) return DocumentType.RFI;
            if (docCode.startsWith("MIN")) return DocumentType.MINUTES;
            if (docCode.startsWith("LOA")) return DocumentType.LOA;
            if (docCode.startsWith("BRD")) return DocumentType.BANK_GUARANTEE;
            if (docCode.startsWith("RPT")) return DocumentType.REPORT;
        }
        return DocumentType.REPORT;
    }

    private DrawingDiscipline mapDrawingDiscipline(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.startsWith("civ")) return DrawingDiscipline.CIVIL;
        if (t.startsWith("struct")) return DrawingDiscipline.STRUCTURAL;
        if (t.startsWith("elec")) return DrawingDiscipline.ELECTRICAL;
        if (t.startsWith("mech")) return DrawingDiscipline.MECHANICAL;
        if (t.startsWith("arch")) return DrawingDiscipline.ARCHITECTURAL;
        if (t.startsWith("plumb")) return DrawingDiscipline.PLUMBING;
        if (t.startsWith("hvac")) return DrawingDiscipline.HVAC;
        if (t.startsWith("ict") || t.contains("network") || t.contains("telecom")) return DrawingDiscipline.ICT;
        if (t.startsWith("manage")) return DrawingDiscipline.MANAGEMENT;
        if (t.startsWith("legal")) return DrawingDiscipline.LEGAL;
        if (t.startsWith("finance")) return DrawingDiscipline.FINANCE;
        return null;
    }

    private DocumentStatus mapDocumentStatus(String text) {
        if (text == null) return DocumentStatus.DRAFT;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("ifc")) return DocumentStatus.IFC;
        if (t.equals("ifa")) return DocumentStatus.IFA;
        if (t.contains("approv")) return DocumentStatus.APPROVED;
        if (t.contains("publish")) return DocumentStatus.PUBLISHED;
        if (t.contains("execut")) return DocumentStatus.EXECUTED;
        if (t.contains("valid")) return DocumentStatus.VALID;
        if (t.contains("closed")) return DocumentStatus.CLOSED;
        if (t.contains("open")) return DocumentStatus.OPEN;
        if (t.contains("under review") || t.contains("review")) return DocumentStatus.UNDER_REVIEW;
        if (t.contains("supersede")) return DocumentStatus.SUPERSEDED;
        if (t.contains("archive")) return DocumentStatus.ARCHIVED;
        if (t.contains("draft")) return DocumentStatus.DRAFT;
        return DocumentStatus.DRAFT;
    }

    private DrawingStatus mapDrawingStatusFromDocStatus(DocumentStatus status) {
        if (status == null) return DrawingStatus.PRELIMINARY;
        return switch (status) {
            case IFC -> DrawingStatus.IFC;
            case IFA -> DrawingStatus.IFA;
            case SUPERSEDED -> DrawingStatus.SUPERSEDED;
            case APPROVED, PUBLISHED -> DrawingStatus.IFC;
            default -> DrawingStatus.PRELIMINARY;
        };
    }

    // =========================================================================
    // M8 Resources
    // =========================================================================
    private void loadResources(Workbook wb, Map<String, UUID> orgIndex) {
        Sheet sheet = wb.getSheet("M8_Resource_Register");
        if (sheet == null) {
            log.warn("[Excel] sheet M8_Resource_Register missing — skipping resources");
            return;
        }
        int seeded = 0;
        for (int r = 3; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String code = stringValue(row.getCell(0));
            if (code == null) continue;
            String name = stringValue(row.getCell(1));
            String typeText = stringValue(row.getCell(2));
            String categoryText = stringValue(row.getCell(3));
            String unitText = stringValue(row.getCell(4));
            // col 5 cpwd rate, col 6 overtime rate — stored in dailyCostLakh approximation only
            Double poolMax = doubleValue(row.getCell(7));
            // col 8 planned, col 9 actual, col 10 utilisation% — captured via daily logs
            String contractorText = stringValue(row.getCell(11));
            // col 12 wbs assignment — informational (Resource.wbsAssignmentId is gone)
            BigDecimal dailyCostLakh = bdValue(row.getCell(13));
            // col 14 cumulative cost crores — derived from rollups; col 15 status — daily log signals

            String typeCode = mapResourceTypeCode(typeText);
            ResourceType rt = resourceFactory.requireType(typeCode);
            String roleCode = mapResourceRoleCode(categoryText, typeCode);
            ResourceRole role = resourceFactory.ensureRole(roleCode, typeCode);

            Resource res = new Resource();
            res.setCode(code);
            res.setName(name != null ? name : code);
            res.setResourceType(rt);
            res.setRole(role);
            res.setUnit(mapResourceUnitCode(unitText));
            res.setAvailability(BigDecimal.valueOf(100));
            // Map dailyCostLakh (1 lakh INR = 100 000) → costPerUnit so cost rollups work.
            if (dailyCostLakh != null && dailyCostLakh.signum() > 0) {
                res.setCostPerUnit(dailyCostLakh.multiply(BigDecimal.valueOf(100_000L))
                    .setScale(4, RoundingMode.HALF_UP));
            }
            res.setStatus(ResourceStatus.ACTIVE);
            res.setSortOrder(0);
            Resource saved = resourceRepository.save(res);

            // Equipment-specific detail row (replaces the old in-Resource fields).
            if ("EQUIPMENT".equals(typeCode)) {
                ResourceEquipmentDetails details = ResourceEquipmentDetails.builder()
                    .resourceId(saved.getId())
                    .quantityAvailable(poolMax != null ? poolMax.intValue() : null)
                    .build();
                resourceEquipmentDetailsRepository.save(details);
            } else if ("MATERIAL".equals(typeCode)) {
                ResourceMaterialDetails details = ResourceMaterialDetails.builder()
                    .resourceId(saved.getId())
                    .baseUnit(mapResourceUnitCode(unitText))
                    .build();
                resourceMaterialDetailsRepository.save(details);
            }

            // Contractor mapping is now informational — Resource.responsibleContractorId is gone.
            UUID contractorOrgId = resolveContractorOrg(contractorText, orgIndex);
            if (contractorOrgId != null) {
                log.debug("[Excel] resource {} → contractor org {}", code, contractorOrgId);
            }
            seeded++;
        }
        entityManager.flush();
        log.info("[Excel] loaded {} resources from M8_Resource_Register", seeded);
    }

    /** Map an Excel free-text type to the canonical {@code ResourceType.code}. */
    private String mapResourceTypeCode(String text) {
        if (text == null) return "LABOR";
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("labour") || t.contains("labor")) return "LABOR";
        if (t.contains("equipment") || t.contains("plant") || t.contains("machine")) return "EQUIPMENT";
        if (t.contains("material")) return "MATERIAL";
        return "LABOR";
    }

    /** Derive a {@link ResourceRole} code from the legacy "category" column free-text. */
    private String mapResourceRoleCode(String text, String typeCode) {
        if (text == null) return "IMPORTED-" + typeCode;
        String t = text.toLowerCase(Locale.ROOT);
        // Labour categories
        if (t.contains("site engineer")) return "SITE_ENGINEER";
        if (t.contains("foreman")) return "FOREMAN";
        if (t.contains("semi-skilled") || t.contains("semi skilled")) return "SKILLED_LABOUR";
        if (t.contains("unskilled")) return "UNSKILLED_LABOUR";
        if (t.contains("skilled")) return "SKILLED_LABOUR";
        if (t.contains("technical") || t.contains("hse")) return "SITE_ENGINEER";
        if (t.contains("operator")) return "OPERATOR";
        if (t.contains("driver")) return "DRIVER";
        if (t.contains("welder")) return "WELDER";
        if (t.contains("electrician")) return "ELECTRICIAN";
        // Equipment categories
        if (t.contains("earth")) return "EARTH_MOVING";
        if (t.contains("crane") || t.contains("lifting")) return "CRANES_LIFTING";
        if (t.contains("concrete")) return "CONCRETE_EQUIPMENT";
        if (t.contains("paving") || t.contains("road works")) return "PAVING_EQUIPMENT";
        if (t.contains("transport") || t.contains("vehicle")) return "TRANSPORT_VEHICLES";
        if (t.contains("piling")) return "PILING_RIG";
        if (t.contains("survey")) return "SURVEY_EQUIPMENT";
        // Material categories
        if (t.contains("cement")) return "CEMENT";
        if (t.contains("steel")) return "STEEL_REBAR";
        if (t.contains("aggregate") || t.contains("sand")) return "AGGREGATE";
        if (t.contains("bitumen") || t.contains("asphalt")) return "BITUMEN";
        if (t.contains("rmc") || t.contains("ready")) return "READY_MIX_CONCRETE";
        if (t.contains("brick") || t.contains("block")) return "BRICKS_BLOCKS";
        if (t.contains("cable")) return "ELECTRICAL_CABLE";
        if (t.contains("formwork")) return "FORMWORK";
        return "IMPORTED-" + typeCode;
    }

    /** Map an Excel free-text unit string to the canonical short code stored in {@code Resource.unit}. */
    private String mapResourceUnitCode(String text) {
        if (text == null) return "PER_DAY";
        String t = text.toLowerCase(Locale.ROOT).replace(" ", "").replace(".", "");
        if (t.contains("perday") || t.contains("/day")) return "PER_DAY";
        if (t.equals("mt") || t.contains("tonne")) return "MT";
        if (t.equals("cum") || t.contains("cu.m") || t.contains("cubic")) return "CU_M";
        if (t.equals("rmt") || t.contains("running")) return "RMT";
        if (t.equals("nos") || t.equals("no") || t.equals("number")) return "NOS";
        if (t.equals("kg")) return "KG";
        if (t.contains("litre") || t.equals("l")) return "LITRE";
        return "PER_DAY";
    }

    // =========================================================================
    // M7 Risks
    // =========================================================================
    private void loadRisks(Workbook wb, UUID projectId) {
        Sheet sheet = wb.getSheet("M7_Risk_Register");
        if (sheet == null) {
            log.warn("[Excel] sheet M7_Risk_Register missing — skipping risks");
            return;
        }
        int seeded = 0;
        for (int r = 3; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String riskId = stringValue(row.getCell(0));
            if (riskId == null) continue;
            String title = stringValue(row.getCell(1));
            // col 2 wbs scope (not stored)
            String categoryText = stringValue(row.getCell(3));
            Integer probability = intValue(row.getCell(4));
            Integer impactCost = intValue(row.getCell(5));
            Integer impactSchedule = intValue(row.getCell(6));
            // col 7 risk score (computed)
            String ragText = stringValue(row.getCell(8));
            // col 9 owner, col 10 strategy, col 11 action
            LocalDate dueDate = dateValue(row.getCell(12));
            Double residualScore = doubleValue(row.getCell(13));
            String trendText = stringValue(row.getCell(14));
            String statusText = stringValue(row.getCell(15));

            Risk risk = new Risk();
            risk.setProjectId(projectId);
            risk.setCode(riskId);
            risk.setTitle(title != null ? title : riskId);
            risk.setCategory(legacyCategoryLookup.byCode(mapRiskCategory(categoryText)));
            if (probability != null && probability >= 1 && probability <= 5) {
                risk.setProbability(RiskProbability.values()[probability - 1]);
            }
            risk.setImpactCost(impactCost);
            risk.setImpactSchedule(impactSchedule);
            risk.setStatus(mapRiskStatus(statusText));
            risk.setTrend(mapRiskTrend(trendText));
            risk.setDueDate(dueDate);
            risk.setResidualRiskScore(residualScore);
            risk.setRiskType(riskId.startsWith("OPP-") ? RiskType.OPPORTUNITY : RiskType.THREAT);
            risk.setIdentifiedDate(LocalDate.of(2024, 9, 15));
            // Honor Excel's explicit RAG column (not the auto-derived banding) so
            // the dashboard counts match the authoritative Excel scenario.
            RiskRag excelRag = mapRiskRag(ragText);
            if (excelRag != null) {
                risk.setRag(excelRag);
            }
            // Safety net: any OPP- code or OPPORTUNITY rag always marks the risk as an opportunity.
            if (risk.isOpportunity() || risk.getRag() == RiskRag.OPPORTUNITY) {
                risk.setRiskType(RiskType.OPPORTUNITY);
                risk.setRag(RiskRag.OPPORTUNITY);
            }
            riskRepository.save(risk);
            seeded++;
        }
        entityManager.flush();
        log.info("[Excel] loaded {} risks from M7_Risk_Register", seeded);
    }

    private RiskRag mapRiskRag(String text) {
        if (text == null) return null;
        String t = text.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("CRIMSON")) return RiskRag.CRIMSON;
        if (t.startsWith("RED")) return RiskRag.RED;
        if (t.startsWith("AMBER") || t.startsWith("YELLOW") || t.startsWith("ORANGE")) return RiskRag.AMBER;
        if (t.startsWith("GREEN")) return RiskRag.GREEN;
        if (t.startsWith("OPP") || t.startsWith("BLUE")) return RiskRag.OPPORTUNITY;
        return null;
    }

    /**
     * Map a free-text Excel category cell to the canonical {@code risk_category_master.code}.
     * Codes here mirror the Liquibase 036 backfill table so re-imports stay consistent.
     */
    private String mapRiskCategory(String text) {
        if (text == null) return "PMO-CHANGE-CONTROL";
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("land")) return "LA-GENERIC";
        if (t.contains("forest") || t.contains("ec") && t.contains("regul")) return "FE-STAGE-I-CLEARANCE";
        if (t.contains("utility")) return "US-GENERIC";
        if (t.contains("regulatory") || t.contains("env") || t.contains("pollution") || t.contains("asi"))
            return "SR-GENERIC";
        if (t.contains("contractor") || t.contains("financial")) return "FIN-WORKING-CAPITAL-CRUNCH";
        if (t.contains("monsoon")) return "MW-FLASH-FLOOD";
        if (t.contains("natural hazard") || t.contains("geotech")) return "FM-EARTHQUAKE";
        if (t.contains("market") || t.contains("inflation") || t.contains("price") || t.contains("currency"))
            return "MP-BITUMEN-ESCALATION";
        if (t.contains("tech") || t.contains("it") || t.contains("design")) return "DT-BIM-CLASH-LATE";
        if (t.contains("supply chain")) return "EG-GENERIC";
        if (t.contains("political")) return "EG-GENERIC";
        if (t.contains("labour") || t.contains("hr") || t.contains("resource")) return "RES-QUARRY-CLOSURE";
        if (t.contains("quality")) return "CQ-GENERIC";
        if (t.contains("scope") || t.contains("variation")) return "PMO-CHANGE-CONTROL";
        if (t.contains("schedule")) return "SCH-CRITICAL-PATH";
        if (t.contains("cost")) return "FIN-GENERIC";
        if (t.contains("opportunity")) return "PMO-CHANGE-CONTROL";
        return "PMO-CHANGE-CONTROL";
    }

    private RiskStatus mapRiskStatus(String text) {
        if (text == null) return RiskStatus.IDENTIFIED;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("escalated")) return RiskStatus.OPEN_ESCALATED;
        if (t.contains("active management")) return RiskStatus.OPEN_UNDER_ACTIVE_MANAGEMENT;
        if (t.contains("being managed")) return RiskStatus.OPEN_BEING_MANAGED;
        if (t.contains("monitor")) return RiskStatus.OPEN_MONITOR;
        if (t.contains("watch")) return RiskStatus.OPEN_WATCH;
        if (t.contains("target")) return RiskStatus.OPEN_TARGET;
        if (t.contains("asi")) return RiskStatus.OPEN_ASI_REVIEW;
        if (t.contains("realised") && t.contains("partial")) return RiskStatus.REALISED_PARTIALLY;
        if (t.contains("resolved")) return RiskStatus.RESOLVED;
        if (t.contains("closed")) return RiskStatus.CLOSED;
        if (t.contains("accepted")) return RiskStatus.ACCEPTED;
        if (t.contains("mitigat")) return RiskStatus.MITIGATING;
        if (t.contains("analyz") || t.contains("analys")) return RiskStatus.ANALYZING;
        if (t.startsWith("open")) return RiskStatus.OPEN_MONITOR;
        return RiskStatus.IDENTIFIED;
    }

    private RiskTrend mapRiskTrend(String text) {
        if (text == null) return RiskTrend.STABLE;
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("worsen")) return RiskTrend.WORSENING;
        if (t.contains("improv")) return RiskTrend.IMPROVING;
        return RiskTrend.STABLE;
    }
}
