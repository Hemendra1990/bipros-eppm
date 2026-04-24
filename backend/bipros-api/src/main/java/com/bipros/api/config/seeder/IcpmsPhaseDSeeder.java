package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.application.service.ResourceUtilisationService;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceCategory;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceUnit;
import com.bipros.resource.domain.model.UtilisationStatus;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IC-PMS Phase D seeder — M6 Document register + M8 Resource daily utilisation.
 *
 * <p>Seeds 30 documents (drawings, specs, RFIs, minutes, reports, LOAs) across a 7-folder
 * register, plus 32 resources (labour + equipment + materials) with daily deployment logs.
 * Resource {@code EQP-EXCAV-20T} is tuned to 93.8% utilisation → {@link UtilisationStatus#OVER_90}.
 *
 * <p>Sentinel: first drawing {@code DRW-N03-P01-001} present → skip.
 */
@Slf4j
@Component
@Profile("dev")
@Order(104)
@RequiredArgsConstructor
public class IcpmsPhaseDSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final DocumentFolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceRateRepository resourceRateRepository;
    private final ResourceUtilisationService utilisationService;
    private final OrganisationRepository organisationRepository;

    /** Cached WBS → contractor map built once per seed run. */
    private Map<String, Organisation> contractorByWbs;

    @Override
    @Transactional
    public void run(String... args) {
        // Sentinel widened so the Excel master-data loader takes precedence:
        // if any document OR resource row exists already, skip (loader owns both tables).
        if (documentRepository.count() > 0 || resourceRepository.count() > 0) {
            log.info("[IC-PMS Phase D] documents/resources already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Phase D] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        seedDocuments(projectId);
        seedResources();

        log.info("[IC-PMS Phase D] seeded 30 documents + 32 resources with daily logs");
    }

    // =========================================================================
    // M6 Documents
    // =========================================================================
    private void seedDocuments(UUID projectId) {
        UUID drawingsFolderId = folder(projectId, "DRAWINGS", "Drawings", DocumentCategory.DRAWING);
        UUID specsFolderId = folder(projectId, "SPECS", "Specifications", DocumentCategory.SPECIFICATION);
        UUID rfiFolderId = folder(projectId, "RFI", "Requests for Information", DocumentCategory.CORRESPONDENCE);
        UUID minutesFolderId = folder(projectId, "MINUTES", "Meeting Minutes", DocumentCategory.CORRESPONDENCE);
        UUID contractsFolderId = folder(projectId, "CONTRACTS", "LOA & Contracts", DocumentCategory.CONTRACT);
        UUID reportsFolderId = folder(projectId, "REPORTS", "Reports", DocumentCategory.GENERAL);
        UUID bgFolderId = folder(projectId, "BG", "Bank Guarantees", DocumentCategory.APPROVAL);

        // 14 Drawings (DRW-*) across multiple disciplines
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-001", "Site Layout — DMIC-N03-P01",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.IFC, "TRM-2025-001");
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-002", "Structural GA — Plant Building",
            DocumentType.DRAWING, DrawingDiscipline.STRUCTURAL, DocumentStatus.IFC, "TRM-2025-001");
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-003", "Electrical Single Line Diagram",
            DocumentType.DRAWING, DrawingDiscipline.ELECTRICAL, DocumentStatus.IFA, "TRM-2025-002");
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-004", "Mechanical Equipment Layout",
            DocumentType.DRAWING, DrawingDiscipline.MECHANICAL, DocumentStatus.IFA, "TRM-2025-002");
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-005", "Architectural Elevation",
            DocumentType.DRAWING, DrawingDiscipline.ARCHITECTURAL, DocumentStatus.PUBLISHED, null);
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P01-006", "HVAC Schematic",
            DocumentType.DRAWING, DrawingDiscipline.HVAC, DocumentStatus.DRAFT, null);
        seedDoc(projectId, drawingsFolderId, "DRW-N03-P02-001", "Township Grid Layout",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.IFC, "TRM-2025-003");
        seedDoc(projectId, drawingsFolderId, "DRW-N04-P01-001", "Logistics Park Site Plan",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.IFC, "TRM-2025-004");
        seedDoc(projectId, drawingsFolderId, "DRW-N04-P02-001", "Road Network — Chainage 0+000",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.IFC, "TRM-2025-005");
        seedDoc(projectId, drawingsFolderId, "DRW-N05-P01-001", "Multi-Modal Terminal Layout",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.IFA, "TRM-2025-006");
        seedDoc(projectId, drawingsFolderId, "DRW-ICT-001", "Fibre Optic Backbone Route",
            DocumentType.DRAWING, DrawingDiscipline.ICT, DocumentStatus.IFC, "TRM-2025-007");
        seedDoc(projectId, drawingsFolderId, "DRW-ICT-002", "Data Centre Rack Layout",
            DocumentType.DRAWING, DrawingDiscipline.ICT, DocumentStatus.IFA, "TRM-2025-007");
        seedDoc(projectId, drawingsFolderId, "DRW-N06-P01-001", "Industrial Zone Master Plan",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.PUBLISHED, null);
        seedDoc(projectId, drawingsFolderId, "DRW-N06-P02-001", "Utility Corridor Cross-Section",
            DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentStatus.PUBLISHED, null);

        // 5 Specifications (SPEC-*)
        seedDoc(projectId, specsFolderId, "SPEC-CIVIL-001", "CPWD General Specifications Rev 2024",
            DocumentType.SPECIFICATION, null, DocumentStatus.PUBLISHED, null);
        seedDoc(projectId, specsFolderId, "SPEC-ELEC-001", "IS Code Electrical Specification",
            DocumentType.SPECIFICATION, null, DocumentStatus.PUBLISHED, null);
        seedDoc(projectId, specsFolderId, "SPEC-HVAC-001", "HVAC Design Basis Report",
            DocumentType.SPECIFICATION, null, DocumentStatus.APPROVED, null);
        seedDoc(projectId, specsFolderId, "SPEC-ICT-001", "ICT Network Specification",
            DocumentType.SPECIFICATION, null, DocumentStatus.APPROVED, null);
        seedDoc(projectId, specsFolderId, "SPEC-MECH-001", "Mechanical Equipment Standards",
            DocumentType.SPECIFICATION, null, DocumentStatus.UNDER_REVIEW, null);

        // 4 RFIs (RFI-*)
        seedDoc(projectId, rfiFolderId, "RFI-N03-001", "Clarification on Rebar Grade",
            DocumentType.RFI, null, DocumentStatus.CLOSED, null);
        seedDoc(projectId, rfiFolderId, "RFI-N03-002", "Site Access Road Alignment",
            DocumentType.RFI, null, DocumentStatus.OPEN, null);
        seedDoc(projectId, rfiFolderId, "RFI-N04-001", "Soil Report Clarification",
            DocumentType.RFI, null, DocumentStatus.CLOSED, null);
        seedDoc(projectId, rfiFolderId, "RFI-N05-001", "Tender Addendum Query",
            DocumentType.RFI, null, DocumentStatus.OPEN, null);

        // 3 Minutes (MIN-*)
        seedDoc(projectId, minutesFolderId, "MIN-PRB-2025-01", "Project Review Board — January 2025",
            DocumentType.MINUTES, null, DocumentStatus.APPROVED, null);
        seedDoc(projectId, minutesFolderId, "MIN-PRB-2025-02", "Project Review Board — February 2025",
            DocumentType.MINUTES, null, DocumentStatus.APPROVED, null);
        seedDoc(projectId, minutesFolderId, "MIN-PRB-2025-03", "Project Review Board — March 2025",
            DocumentType.MINUTES, null, DocumentStatus.DRAFT, null);

        // 2 LOAs (LOA-*)
        seedDoc(projectId, contractsFolderId, "LOA-N03-P01", "LOA — L&T EPC for DMIC-N03-P01",
            DocumentType.LOA, null, DocumentStatus.EXECUTED, null);
        seedDoc(projectId, contractsFolderId, "LOA-N04-P01", "LOA — Tata Projects for DMIC-N04-P01",
            DocumentType.LOA, null, DocumentStatus.EXECUTED, null);

        // 1 Bank Guarantee (BRD-*)
        seedDoc(projectId, bgFolderId, "BRD-N03-P01", "Performance Bank Guarantee — L&T",
            DocumentType.BANK_GUARANTEE, null, DocumentStatus.VALID, null);

        // 1 Report (RPT-*)
        seedDoc(projectId, reportsFolderId, "RPT-MPR-202503", "Monthly Progress Report — March 2025",
            DocumentType.REPORT, null, DocumentStatus.PUBLISHED, null);
    }

    private UUID folder(UUID projectId, String code, String name, DocumentCategory category) {
        DocumentFolder folder = new DocumentFolder();
        folder.setProjectId(projectId);
        folder.setCode(code);
        folder.setName(name);
        folder.setCategory(category);
        folder.setSortOrder(0);
        return folderRepository.save(folder).getId();
    }

    private void seedDoc(UUID projectId, UUID folderId, String docNumber, String title,
                         DocumentType type, DrawingDiscipline discipline,
                         DocumentStatus status, String transmittalNumber) {
        Document doc = new Document();
        doc.setProjectId(projectId);
        doc.setFolderId(folderId);
        doc.setDocumentNumber(docNumber);
        doc.setTitle(title);
        doc.setFileName(docNumber + ".pdf");
        doc.setFileSize(524288L);
        doc.setMimeType("application/pdf");
        doc.setFilePath("/docs/" + docNumber + ".pdf");
        doc.setCurrentVersion(1);
        doc.setStatus(status);
        doc.setDocumentType(type);
        doc.setDiscipline(discipline);
        doc.setTransmittalNumber(transmittalNumber);
        // IC-PMS M6 denormalised approval fields — used by the document register grid
        doc.setWbsPackageCode(deriveWbsPackage(docNumber));
        doc.setIssuedBy(deriveIssuedBy(type));
        doc.setIssuedDate(LocalDate.of(2025, 2, 15));
        if (isApprovedStatus(status)) {
            doc.setApprovedBy("Employer/PMC");
            doc.setApprovedDate(LocalDate.of(2025, 3, 5));
        }
        documentRepository.save(doc);
    }

    /** Extract the WBS package from the document number (e.g. DRW-N03-P01-001 → DMIC-N03-P01). */
    private String deriveWbsPackage(String docNumber) {
        if (docNumber == null) return null;
        String[] parts = docNumber.split("-");
        if (parts.length >= 3 && parts[1].startsWith("N") && parts[2].startsWith("P")) {
            return "DMIC-" + parts[1] + "-" + parts[2];
        }
        if (parts.length >= 2 && "ICT".equals(parts[1])) {
            return "DMIC-ICT";
        }
        if (parts.length >= 2 && parts[1].startsWith("N")) {
            return "DMIC-" + parts[1];
        }
        return "DMIC-PROG";
    }

    private String deriveIssuedBy(DocumentType type) {
        if (type == null) return "EPC Contractor";
        return switch (type) {
            case RFI, MINUTES, REPORT -> "PMC";
            case LOA, BANK_GUARANTEE -> "Employer";
            case SPECIFICATION -> "PMC (Technical)";
            default -> "EPC Contractor";
        };
    }

    private boolean isApprovedStatus(DocumentStatus status) {
        return status == DocumentStatus.IFC
            || status == DocumentStatus.APPROVED
            || status == DocumentStatus.PUBLISHED
            || status == DocumentStatus.EXECUTED
            || status == DocumentStatus.VALID
            || status == DocumentStatus.CLOSED;
    }

    // =========================================================================
    // M8 Resources + daily logs
    // =========================================================================
    private void seedResources() {
        LocalDate today = LocalDate.of(2025, 4, 15);

        // Resolve EPC contractors once — map WBS package → responsible contractor
        this.contractorByWbs = buildContractorByWbsMap();

        // ---- Equipment (10) ----
        // EQP-EXCAV-20T is tuned to 93.8% → OVER_90 (Excel scenario)
        seedResource("EQP-EXCAV-20T", "Excavator 20T", ResourceType.NONLABOR,
            ResourceCategory.EARTH_MOVING, ResourceUnit.PER_DAY, 16.0, today, 15.0, 14.07,
            bd("42.50"), bd("385.20"), "DMIC-N03-P01");
        seedResource("EQP-EXCAV-10T", "Excavator 10T", ResourceType.NONLABOR,
            ResourceCategory.EARTH_MOVING, ResourceUnit.PER_DAY, 12.0, today, 10.0, 8.5,
            bd("22.00"), bd("198.00"), "DMIC-N03-P01");
        seedResource("EQP-BULLDOZER", "Bulldozer D85", ResourceType.NONLABOR,
            ResourceCategory.EARTH_MOVING, ResourceUnit.PER_DAY, 8.0, today, 7.0, 7.2,
            bd("36.00"), bd("312.50"), "DMIC-N03-P02");
        seedResource("EQP-CRANE-50T", "Mobile Crane 50T", ResourceType.NONLABOR,
            ResourceCategory.CRANES_LIFTING, ResourceUnit.PER_DAY, 6.0, today, 5.0, 5.0,
            bd("28.00"), bd("198.00"), "DMIC-N04-P01");
        seedResource("EQP-TRANSIT-MIXER", "Transit Mixer 6 CuM", ResourceType.NONLABOR,
            ResourceCategory.CONCRETE_EQUIPMENT, ResourceUnit.PER_DAY, 20.0, today, 18.0, 17.5,
            bd("18.00"), bd("145.30"), "DMIC-N03-P01");
        seedResource("EQP-BATCHING-PLANT", "Batching Plant 30 CuM/hr", ResourceType.NONLABOR,
            ResourceCategory.CONCRETE_EQUIPMENT, ResourceUnit.PER_DAY, 4.0, today, 4.0, 4.0,
            bd("52.00"), bd("412.00"), "DMIC-N03-P01");
        seedResource("EQP-PAVER", "Asphalt Paver Vögele", ResourceType.NONLABOR,
            ResourceCategory.PAVING_EQUIPMENT, ResourceUnit.PER_DAY, 4.0, today, 3.0, 2.8,
            bd("46.00"), bd("256.80"), "DMIC-N04-P02");
        seedResource("EQP-DUMPER-25T", "Dumper 25T", ResourceType.NONLABOR,
            ResourceCategory.TRANSPORT_VEHICLES, ResourceUnit.PER_DAY, 30.0, today, 25.0, 23.0,
            bd("18.50"), bd("142.80"), "DMIC-N03-P01");
        seedResource("EQP-PILING-RIG", "Piling Rig Bauer BG28", ResourceType.NONLABOR,
            ResourceCategory.PILING_RIG, ResourceUnit.PER_DAY, 3.0, today, 2.0, 2.0,
            bd("68.00"), bd("285.40"), "DMIC-N05-P01");
        seedResource("EQP-TOTAL-STATION", "Total Station Leica TS16", ResourceType.NONLABOR,
            ResourceCategory.SURVEY_EQUIPMENT, ResourceUnit.PER_DAY, 12.0, today, 10.0, 9.0,
            bd("3.20"), bd("22.00"), "DMIC-N03-P01");

        // ---- Labour (12) ----
        seedResource("LAB-SITE-ENG", "Site Engineer", ResourceType.LABOR,
            ResourceCategory.SITE_ENGINEER, ResourceUnit.PER_DAY, 45.0, today, 40.0, 38.0,
            bd("14.40"), bd("125.60"), "DMIC-N03-P01");
        seedResource("LAB-FOREMAN", "Foreman", ResourceType.LABOR,
            ResourceCategory.FOREMAN, ResourceUnit.PER_DAY, 60.0, today, 55.0, 52.0,
            bd("12.80"), bd("102.40"), "DMIC-N03-P01");
        seedResource("LAB-SKILLED-MASON", "Skilled Mason", ResourceType.LABOR,
            ResourceCategory.SKILLED_LABOUR, ResourceUnit.PER_DAY, 200.0, today, 180.0, 172.0,
            bd("15.48"), bd("142.60"), "DMIC-N03-P01");
        seedResource("LAB-UNSKILLED", "Unskilled Labour", ResourceType.LABOR,
            ResourceCategory.UNSKILLED_LABOUR, ResourceUnit.PER_DAY, 500.0, today, 450.0, 425.0,
            bd("25.50"), bd("232.00"), "DMIC-N03-P01");
        seedResource("LAB-EQP-OPERATOR", "Equipment Operator", ResourceType.LABOR,
            ResourceCategory.OPERATOR, ResourceUnit.PER_DAY, 80.0, today, 72.0, 68.0,
            bd("10.20"), bd("94.40"), "DMIC-N03-P01");
        seedResource("LAB-DRIVER", "Dumper Driver", ResourceType.LABOR,
            ResourceCategory.DRIVER, ResourceUnit.PER_DAY, 60.0, today, 52.0, 50.0,
            bd("6.50"), bd("58.40"), "DMIC-N03-P02");
        seedResource("LAB-WELDER", "Certified Welder 6G", ResourceType.LABOR,
            ResourceCategory.WELDER, ResourceUnit.PER_DAY, 30.0, today, 25.0, 24.0,
            bd("7.20"), bd("62.80"), "DMIC-N04-P01");
        seedResource("LAB-ELECTRICIAN", "Certified Electrician", ResourceType.LABOR,
            ResourceCategory.ELECTRICIAN, ResourceUnit.PER_DAY, 25.0, today, 20.0, 18.0,
            bd("5.40"), bd("42.00"), "DMIC-N03-P01");
        seedResource("LAB-SITE-ENG-N04", "Site Engineer — N04", ResourceType.LABOR,
            ResourceCategory.SITE_ENGINEER, ResourceUnit.PER_DAY, 25.0, today, 22.0, 20.0,
            bd("8.00"), bd("64.20"), "DMIC-N04-P01");
        seedResource("LAB-FOREMAN-N04", "Foreman — N04", ResourceType.LABOR,
            ResourceCategory.FOREMAN, ResourceUnit.PER_DAY, 30.0, today, 28.0, 26.0,
            bd("6.40"), bd("52.80"), "DMIC-N04-P02");
        seedResource("LAB-UNSKILLED-N05", "Unskilled Labour — N05", ResourceType.LABOR,
            ResourceCategory.UNSKILLED_LABOUR, ResourceUnit.PER_DAY, 250.0, today, 220.0, 210.0,
            bd("12.60"), bd("98.40"), "DMIC-N05-P01");
        seedResource("LAB-SKILLED-N06", "Skilled Labour — N06", ResourceType.LABOR,
            ResourceCategory.SKILLED_LABOUR, ResourceUnit.PER_DAY, 100.0, today, 85.0, 80.0,
            bd("7.20"), bd("56.40"), "DMIC-N06-P01");

        // ---- Materials (10) ----
        seedResource("MAT-CEMENT-OPC43", "OPC Cement 43 Grade", ResourceType.MATERIAL,
            ResourceCategory.CEMENT, ResourceUnit.MT, 500.0, today, 120.0, 118.0,
            bd("47.20"), bd("1250.80"), "DMIC-N03-P01");
        seedResource("MAT-CEMENT-PPC", "PPC Cement", ResourceType.MATERIAL,
            ResourceCategory.CEMENT, ResourceUnit.MT, 300.0, today, 75.0, 72.0,
            bd("28.80"), bd("680.50"), "DMIC-N04-P01");
        seedResource("MAT-STEEL-FE500", "TMT Rebar Fe500D", ResourceType.MATERIAL,
            ResourceCategory.STEEL_REBAR, ResourceUnit.MT, 800.0, today, 180.0, 175.0,
            bd("122.50"), bd("3240.80"), "DMIC-N03-P01");
        seedResource("MAT-AGG-20MM", "Coarse Aggregate 20mm", ResourceType.MATERIAL,
            ResourceCategory.AGGREGATE, ResourceUnit.CU_M, 2500.0, today, 850.0, 820.0,
            bd("14.76"), bd("382.40"), "DMIC-N03-P01");
        seedResource("MAT-SAND-RIVER", "River Sand", ResourceType.MATERIAL,
            ResourceCategory.AGGREGATE, ResourceUnit.CU_M, 1500.0, today, 620.0, 595.0,
            bd("12.50"), bd("312.60"), "DMIC-N03-P02");
        seedResource("MAT-BITUMEN-VG30", "Bitumen VG30", ResourceType.MATERIAL,
            ResourceCategory.BITUMEN, ResourceUnit.MT, 200.0, today, 45.0, 42.0,
            bd("25.20"), bd("425.00"), "DMIC-N04-P02");
        seedResource("MAT-RMC-M30", "Ready Mix Concrete M30", ResourceType.MATERIAL,
            ResourceCategory.READY_MIX_CONCRETE, ResourceUnit.CU_M, 600.0, today, 180.0, 175.0,
            bd("105.00"), bd("2425.00"), "DMIC-N03-P01");
        seedResource("MAT-BRICKS", "Clay Bricks Class-A", ResourceType.MATERIAL,
            ResourceCategory.BRICKS_BLOCKS, ResourceUnit.NOS, 500000.0, today, 120000.0, 115000.0,
            bd("8.05"), bd("125.40"), "DMIC-N03-P01");
        seedResource("MAT-CABLE-LT", "LT Cable XLPE 3.5Cx300mm²", ResourceType.MATERIAL,
            ResourceCategory.ELECTRICAL_CABLE, ResourceUnit.RMT, 5000.0, today, 800.0, 750.0,
            bd("62.50"), bd("412.00"), "DMIC-N04-P01");
        seedResource("MAT-FORMWORK", "Modular Aluminium Formwork", ResourceType.MATERIAL,
            ResourceCategory.FORMWORK, ResourceUnit.CU_M, 1200.0, today, 400.0, 380.0,
            bd("19.00"), bd("285.40"), "DMIC-N03-P01");

        log.info("[IC-PMS Phase D] EQP-EXCAV-20T utilisation={}% → {}",
            resourceRepository.findByCode("EQP-EXCAV-20T")
                .map(Resource::getUtilisationPercent).orElse(null),
            resourceRepository.findByCode("EQP-EXCAV-20T")
                .map(Resource::getUtilisationStatus).orElse(null));
    }

    private void seedResource(String code, String name, ResourceType type,
                              ResourceCategory category, ResourceUnit unit,
                              Double poolMax, LocalDate logDate,
                              Double planned, Double actual,
                              BigDecimal dailyCostLakh, BigDecimal cumulativeCrores,
                              String wbsPackage) {
        Resource r = new Resource();
        r.setCode(code);
        r.setName(name);
        r.setResourceType(type);
        r.setResourceCategory(category);
        r.setUnit(unit);
        r.setPoolMaxAvailable(poolMax);
        r.setMaxUnitsPerDay(poolMax);
        r.setDailyCostLakh(dailyCostLakh);
        r.setCumulativeCostCrores(cumulativeCrores);
        r.setWbsAssignmentId(wbsPackage);
        if (contractorByWbs != null) {
            Organisation contractor = contractorByWbs.get(wbsPackage);
            if (contractor != null) {
                r.setResponsibleContractorId(contractor.getId());
                r.setResponsibleContractorName(contractor.getShortName());
            }
        }
        r.setStatus(ResourceStatus.ACTIVE);
        r.setSortOrder(0);
        // Derive hourly rate from dailyCostLakh so ResourceResponse has non-zero rate fields
        // (BUG-027): 1 lakh = 100 000 INR, standard working day = 8 hours.
        double hourly = dailyCostLakh != null
            ? dailyCostLakh.doubleValue() * 100_000d / 8d
            : 0d;
        r.setHourlyRate(hourly);
        r.setOvertimeRate(hourly * 1.5);
        Resource saved = resourceRepository.save(r);

        // Seed standard + overtime ResourceRate rows so /resources/{id}/rates is non-empty
        // and assignment cost derivation has something to multiply against (BUG-029).
        if (dailyCostLakh != null && dailyCostLakh.signum() > 0) {
            BigDecimal standardRate = dailyCostLakh.multiply(BigDecimal.valueOf(100_000))
                .divide(BigDecimal.valueOf(8), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal overtimeRate = standardRate.multiply(BigDecimal.valueOf(1.5));
            persistRate(saved.getId(), "STANDARD", standardRate);
            persistRate(saved.getId(), "OVERTIME", overtimeRate);
        }

        // Record daily log — aggregator computes utilisation% and sets band
        utilisationService.recordDaily(saved.getId(), logDate, planned, actual, wbsPackage, null);
    }

    private void persistRate(UUID resourceId, String rateType, BigDecimal pricePerUnit) {
        ResourceRate rate = new ResourceRate();
        rate.setResourceId(resourceId);
        rate.setRateType(rateType);
        rate.setPricePerUnit(pricePerUnit);
        rate.setEffectiveDate(LocalDate.of(2024, 1, 1));
        resourceRateRepository.save(rate);
    }

    /** IC-PMS M8 WBS → EPC contractor mapping per Excel M5_Contract_Register. */
    private Map<String, Organisation> buildContractorByWbsMap() {
        Map<String, Organisation> byWbs = new HashMap<>();
        Organisation lnt = organisationRepository.findByCode("LNT-IDPL").orElse(null);
        Organisation tata = organisationRepository.findByCode("TATA-PROJ").orElse(null);
        Organisation afcons = organisationRepository.findByCode("AFCONS").orElse(null);
        Organisation hcc = organisationRepository.findByCode("HCC").orElse(null);
        Organisation dilip = organisationRepository.findByCode("DILIP-BUILDCON").orElse(null);
        if (lnt != null) {
            byWbs.put("DMIC-N03-P01", lnt);
            byWbs.put("DMIC-N03-P02", lnt);
        }
        if (tata != null) {
            byWbs.put("DMIC-N04-P01", tata);
        }
        if (afcons != null) {
            byWbs.put("DMIC-N04-P02", afcons);
            byWbs.put("DMIC-N05-P01", afcons);
        }
        if (hcc != null) {
            byWbs.put("DMIC-N06-P01", hcc);
        }
        if (dilip != null) {
            byWbs.put("DMIC-N06-P02", dilip);
        }
        return byWbs;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
