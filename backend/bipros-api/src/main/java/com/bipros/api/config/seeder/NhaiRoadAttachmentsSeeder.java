package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.util.CorridorGeometry;
import com.bipros.api.config.seeder.util.MinimalPdfGenerator;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.ContractAttachment;
import com.bipros.contract.domain.model.ContractAttachmentType;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentCategory;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DocumentVersion;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DocumentVersionRepository;
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
import com.bipros.integration.storage.RasterStorage;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NH-48 Rajasthan road project — fills the document register, contract attachments, GIS
 * polygons, satellite imagery, and construction-progress snapshots that the main
 * {@link NhaiRoadProjectSeeder} (Order 140) leaves blank.
 *
 * <p>Implements the design at
 * {@code docs/superpowers/specs/2026-04-25-odisha-sh10-and-attachments-design.md}.
 *
 * <p>Sentinel: project lookup by code {@code BIPROS/NHAI/RJ/2025/001}. Whole seeder no-ops
 * if any documents already exist for the project.
 *
 * <p>Source files (5 PDFs + 5 JPEGs + 1 DOCX) live on the classpath under
 * {@code seed-data/road-project/documents/} and {@code seed-data/road-project/satellite/}.
 * For the 23 register entries with no real PDF on disk, a stub PDF is generated at runtime
 * via {@link MinimalPdfGenerator}.
 */
@Slf4j
@Component
@Profile("dev")
@Order(145)
@RequiredArgsConstructor
public class NhaiRoadAttachmentsSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "BIPROS/NHAI/RJ/2025/001";
    private static final String DOCS_RESOURCE_ROOT = "seed-data/road-project/documents/";
    private static final String SATELLITE_RESOURCE_ROOT = "seed-data/road-project/satellite/";
    private static final Path DOCUMENT_STORAGE_ROOT = Paths.get("./storage/documents").toAbsolutePath().normalize();
    private static final Path CONTRACT_STORAGE_ROOT = Paths.get("./storage/contracts").toAbsolutePath().normalize();

    // NH-48 Rajasthan corridor — Ch 145+000 to Ch 165+000 mapped to lat/lon.
    private static final double NH48_START_LAT = 26.6500;
    private static final double NH48_START_LON = 73.5000;
    private static final double NH48_END_LAT   = 26.7800;
    private static final double NH48_END_LON   = 73.6500;
    private static final double CORRIDOR_HALF_WIDTH_M = 25.0;

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFolderRepository documentFolderRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ContractRepository contractRepository;
    private final ContractAttachmentRepository contractAttachmentRepository;
    private final GisLayerRepository gisLayerRepository;
    private final WbsPolygonRepository wbsPolygonRepository;
    private final SatelliteImageRepository satelliteImageRepository;
    private final ConstructionProgressSnapshotRepository progressSnapshotRepository;
    private final RasterStorage rasterStorage;

    @Override
    public void run(String... args) {
        Optional<Project> opt = projectRepository.findByCode(PROJECT_CODE);
        if (opt.isEmpty()) {
            log.warn("[NH-48 attachments] project '{}' not found — skipping (run NhaiRoadProjectSeeder first)", PROJECT_CODE);
            return;
        }
        Project project = opt.get();
        long existing = documentRepository.findByProjectId(project.getId()).size();
        if (existing > 0) {
            log.info("[NH-48 attachments] project '{}' already has {} documents — skipping", PROJECT_CODE, existing);
            return;
        }

        log.info("[NH-48 attachments] seeding documents, contract attachments, GIS, and satellite imagery for '{}'", PROJECT_CODE);

        var folders = ensureFolders(project.getId());
        seedDocuments(project, folders);
        seedContractAttachments(project);
        var polygons = seedGisPolygons(project);
        seedSatelliteAndProgress(project, polygons);

        log.info("[NH-48 attachments] done.");
    }

    // ────────────────────────── Folders ──────────────────────────

    private record Folders(UUID drawings, UUID specs, UUID methodStatements, UUID itps, UUID plans, UUID reference) {}

    private Folders ensureFolders(UUID projectId) {
        return new Folders(
                ensureFolder(projectId, "DRAWINGS", "Drawings", DocumentCategory.DRAWING, 10),
                ensureFolder(projectId, "SPECS", "Specifications", DocumentCategory.SPECIFICATION, 20),
                ensureFolder(projectId, "MS", "Method Statements", DocumentCategory.GENERAL, 30),
                ensureFolder(projectId, "ITPS", "Inspection & Test Plans", DocumentCategory.GENERAL, 40),
                ensureFolder(projectId, "PLANS", "Plans & Programmes", DocumentCategory.GENERAL, 50),
                ensureFolder(projectId, "REFERENCE", "Reference & Frameworks", DocumentCategory.GENERAL, 60));
    }

    private UUID ensureFolder(UUID projectId, String code, String name, DocumentCategory category, int sortOrder) {
        return documentFolderRepository.findAll().stream()
                .filter(f -> projectId.equals(f.getProjectId()) && code.equals(f.getCode()))
                .map(DocumentFolder::getId)
                .findFirst()
                .orElseGet(() -> {
                    DocumentFolder f = new DocumentFolder();
                    f.setProjectId(projectId);
                    f.setName(name);
                    f.setCode(code);
                    f.setCategory(category);
                    f.setSortOrder(sortOrder);
                    return documentFolderRepository.save(f).getId();
                });
    }

    // ────────────────────────── Documents ──────────────────────────

    /** Single register entry — drives both Document insertion and binary resolution. */
    private record DocEntry(
            String docNumber,
            String title,
            String category,
            String specRef,
            String issuedBy,
            String approvedBy,
            String classification,
            String binaryClasspathName,
            DocumentType type,
            DrawingDiscipline discipline,
            DocumentCategory folderCategory) {}

    private void seedDocuments(Project project, Folders folders) {
        List<DocEntry> entries = registerEntries();
        int seeded = 0;
        for (DocEntry e : entries) {
            UUID folderId = switch (e.folderCategory()) {
                case DRAWING -> folders.drawings();
                case SPECIFICATION -> folders.specs();
                default -> resolveOtherFolder(e, folders);
            };

            byte[] binary = loadBinary(DOCS_RESOURCE_ROOT + e.binaryClasspathName())
                    .orElseGet(() -> MinimalPdfGenerator.render(
                            e.docNumber(), e.title(), e.category(), e.specRef(),
                            project.getName(), e.approvedBy(),
                            e.classification()));
            String mimeType = e.binaryClasspathName().toLowerCase().endsWith(".docx")
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    : "application/pdf";

            String fileName = sanitizeFileName(e.binaryClasspathName());
            UUID contentId = UUID.randomUUID();
            String relativePath = project.getId() + "/" + contentId + "/v1/" + fileName;
            writeBinary(DOCUMENT_STORAGE_ROOT.resolve(relativePath), binary);

            Document doc = new Document();
            doc.setFolderId(folderId);
            doc.setProjectId(project.getId());
            doc.setDocumentNumber(e.docNumber());
            doc.setTitle(e.title());
            doc.setDescription(e.category() + " — " + e.specRef());
            doc.setFileName(fileName);
            doc.setFileSize((long) binary.length);
            doc.setMimeType(mimeType);
            doc.setFilePath(relativePath);
            doc.setCurrentVersion(1);
            doc.setStatus(DocumentStatus.APPROVED);
            doc.setDocumentType(e.type());
            doc.setDiscipline(e.discipline());
            doc.setIssuedBy(e.issuedBy());
            doc.setIssuedDate(LocalDate.of(2025, 2, 15));
            doc.setApprovedBy(e.approvedBy());
            doc.setApprovedDate(LocalDate.of(2025, 3, 1));
            doc.setWbsPackageCode(deriveWbsPackageCode(e.docNumber()));
            doc.setTags("nh-48,rajasthan," + e.category().toLowerCase().replace(' ', '-'));
            Document savedDoc = documentRepository.save(doc);

            DocumentVersion version = new DocumentVersion();
            version.setDocumentId(savedDoc.getId());
            version.setVersionNumber(1);
            version.setFileName(fileName);
            version.setFilePath(relativePath);
            version.setFileSize((long) binary.length);
            version.setChangeDescription("Initial issue (seeded)");
            version.setUploadedBy("seeder");
            version.setUploadedAt(Instant.now());
            documentVersionRepository.save(version);

            seeded++;
        }
        log.info("[NH-48 attachments] seeded {} documents (folders: drawings/specs/MS/ITPs/plans/reference)", seeded);
    }

    private UUID resolveOtherFolder(DocEntry e, Folders folders) {
        String prefix = e.docNumber().split("/")[0];
        return switch (prefix) {
            case "MS" -> folders.methodStatements();
            case "ITP" -> folders.itps();
            case "PLAN" -> folders.plans();
            case "REF" -> folders.reference();
            default -> folders.plans();
        };
    }

    /**
     * 28 register entries based on the workbook's Approved Documents sheet, plus the
     * Risk Master Metadata DOCX as REF/001.
     */
    private List<DocEntry> registerEntries() {
        return List.of(
                // Drawings (7)
                new DocEntry("DRG/NH48/001", "General Alignment Plan — Ch 145+000 to 165+000", "Drawing", "IRC:73-2018",
                        "Design Consultant", "PMC", "Approved", "drg-nh48-001-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/002", "Pavement Design — Flexible Pavement Cross-Section", "Drawing", "IRC:37-2018",
                        "Design Consultant", "PMC", "Approved", "DRG-NH48-002.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/003", "Cross-Drainage Structure — Box Culvert at Ch 152+300", "Drawing", "IRC:78-2014",
                        "Design Consultant", "PMC", "Approved", "drg-nh48-003-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/004", "Bridge Substructure — Pier Details", "Drawing", "IRC:78-2014",
                        "Bridge Design Cell", "PMC", "Approved", "drg-nh48-004-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.STRUCTURAL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/005", "Drainage System — Side Drains and Catch Water Drains", "Drawing", "IRC:SP:50",
                        "Design Consultant", "PMC", "Approved", "drg-nh48-005-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/006", "Road Furniture — Signage and Markings Layout", "Drawing", "IRC:67-2012",
                        "Design Consultant", "PMC", "Approved", "drg-nh48-006-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),
                new DocEntry("DRG/NH48/007", "Toll Plaza General Arrangement", "Drawing", "MoRTH Toll Manual",
                        "Design Consultant", "PMC", "Approved", "drg-nh48-007-stub.pdf",
                        DocumentType.DRAWING, DrawingDiscipline.CIVIL, DocumentCategory.DRAWING),

                // Specifications (5)
                new DocEntry("SPEC/NH48/001", "MoRTH Specifications for Roads & Bridge Works (5th Revision)", "Specification", "MoRTH 2013",
                        "MoRTH", "NHAI", "Adopted", "spec-nh48-001-stub.pdf",
                        DocumentType.SPECIFICATION, null, DocumentCategory.SPECIFICATION),
                new DocEntry("SPEC/NH48/002", "GSB & WMM Material Specifications", "Specification", "MoRTH Sec 401 & 405",
                        "Design Consultant", "PMC", "Approved", "SPEC-NH48-002.pdf",
                        DocumentType.SPECIFICATION, null, DocumentCategory.SPECIFICATION),
                new DocEntry("SPEC/NH48/003", "Bituminous Mix Design — DBM and BC", "Specification", "MoRTH Sec 504 & 507",
                        "Design Consultant", "PMC", "Approved", "spec-nh48-003-stub.pdf",
                        DocumentType.SPECIFICATION, null, DocumentCategory.SPECIFICATION),
                new DocEntry("SPEC/NH48/004", "Concrete M25/M30 — Mix Design and Sampling", "Specification", "IS 10262-2019",
                        "QC Engineer", "PMC", "Approved", "spec-nh48-004-stub.pdf",
                        DocumentType.SPECIFICATION, null, DocumentCategory.SPECIFICATION),
                new DocEntry("SPEC/NH48/005", "Earthwork — Borrow Material Acceptance Criteria", "Specification", "MoRTH Sec 305",
                        "QC Engineer", "PMC", "Approved", "spec-nh48-005-stub.pdf",
                        DocumentType.SPECIFICATION, null, DocumentCategory.SPECIFICATION),

                // Method Statements (5)
                new DocEntry("MS/NH48/001", "Earthwork Method Statement", "Method Statement", "MoRTH Sec 301-305",
                        "Site Engineer", "PMC", "Approved", "ms-nh48-001-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("MS/NH48/002", "GSB & WMM Laying Method Statement", "Method Statement", "MoRTH Sec 401 & 405",
                        "Site Engineer", "PMC", "Approved", "ms-nh48-002-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("MS/NH48/003", "Bituminous Paving — DBM and BC Method Statement", "Method Statement", "MoRTH Sec 504 & 507",
                        "Site Engineer", "PMC", "Approved", "MS-NH48-003.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("MS/NH48/004", "Bridge Pier Casting — RCC Method Statement", "Method Statement", "IRC:78-2014",
                        "Bridge Site Engineer", "PMC", "Approved", "ms-nh48-004-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("MS/NH48/005", "Box Culvert Construction Method Statement", "Method Statement", "IRC:78-2014",
                        "Site Engineer", "PMC", "Approved", "ms-nh48-005-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),

                // ITPs (5)
                new DocEntry("ITP/NH48/001", "Earthwork Inspection & Test Plan", "ITP", "MoRTH Sec 301-305",
                        "QC Engineer", "PMC", "Approved", "ITP-NH48-001.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("ITP/NH48/002", "Sub-Base & Base Courses ITP", "ITP", "MoRTH Sec 401 & 405",
                        "QC Engineer", "PMC", "Approved", "itp-nh48-002-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("ITP/NH48/003", "Bituminous Layers ITP", "ITP", "MoRTH Sec 504 & 507",
                        "QC Engineer", "PMC", "Approved", "itp-nh48-003-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("ITP/NH48/004", "RCC Structures ITP — M25/M30 Concrete", "ITP", "IS 456:2000",
                        "QC Engineer", "PMC", "Approved", "itp-nh48-004-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("ITP/NH48/005", "Cross-Drainage Works ITP", "ITP", "IRC:SP:13",
                        "QC Engineer", "PMC", "Approved", "itp-nh48-005-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),

                // Plans (6)
                new DocEntry("PLAN/NH48/001", "Project Execution Plan (PEP)", "Plan", "ISO 21500",
                        "Project Manager", "PMC", "Approved", "plan-nh48-001-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("PLAN/NH48/002", "Project Health, Safety & Environment Plan", "Plan", "IS 14489 / OHSAS 18001",
                        "HSE Manager", "PMC", "Approved", "plan-nh48-002-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("PLAN/NH48/003", "Traffic Management Plan", "Plan", "IRC:SP:55",
                        "Traffic Engineer", "PMC", "Approved", "plan-nh48-003-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("PLAN/NH48/004", "Project Quality Plan", "Plan", "ISO 9001:2015",
                        "QC Manager", "PMC", "Approved", "PLAN-NH48-004.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("PLAN/NH48/005", "Environmental Management Plan (EMP)", "Plan", "MoEFCC EIA Notification",
                        "Environment Officer", "MoEFCC", "Approved", "plan-nh48-005-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),
                new DocEntry("PLAN/NH48/006", "Procurement & Resource Plan", "Plan", "Project PEP Annex C",
                        "Procurement Manager", "PMC", "Approved", "plan-nh48-006-stub.pdf",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL),

                // Reference framework (1)
                new DocEntry("REF/NH48/001", "Risk Master Metadata — Road Construction Framework v1.0", "Reference", "IS 15883",
                        "PMC Risk Manager", "Project Director", "Project Confidential", "Risk-Master-Metadata.docx",
                        DocumentType.REPORT, null, DocumentCategory.GENERAL)
        );
    }

    private static String deriveWbsPackageCode(String docNumber) {
        if (docNumber.startsWith("DRG/NH48/00")) {
            int n = Integer.parseInt(docNumber.substring("DRG/NH48/00".length()));
            return n <= 2 ? "WBS-3" : (n <= 4 ? "WBS-6" : (n == 5 ? "WBS-4" : "WBS-5"));
        }
        if (docNumber.startsWith("MS/NH48/")) {
            int n = Integer.parseInt(docNumber.substring("MS/NH48/00".length()));
            return n == 1 ? "WBS-1" : (n == 2 ? "WBS-2" : (n == 3 ? "WBS-3" : (n == 4 ? "WBS-6" : "WBS-4")));
        }
        return null;
    }

    // ────────────────────────── Contract attachments ──────────────────────────

    private void seedContractAttachments(Project project) {
        List<Contract> contracts = contractRepository.findByProjectId(project.getId());
        if (contracts.isEmpty()) {
            log.warn("[NH-48 attachments] no contracts for project — contract attachment seeding skipped");
            return;
        }
        Contract main = contracts.stream()
                .min(Comparator.comparing(Contract::getContractNumber))
                .orElse(contracts.get(0));

        int created = 0;
        // Contract-level
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.CONTRACT, main.getId(),
                "Letter-of-Award.pdf", ContractAttachmentType.LOA, "NHAI Letter of Award — main EPC contract");
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.CONTRACT, main.getId(),
                "Contract-Agreement.pdf", ContractAttachmentType.AGREEMENT, "Signed contract agreement (FIDIC Yellow)");
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.CONTRACT, main.getId(),
                "BOQ-Volume-2.pdf", ContractAttachmentType.BOQ, "BOQ Volume 2 — Priced bill of quantities");
        // Performance bond level (synthesised parent id — works for demo since FK is app-side)
        UUID bondId = UUID.randomUUID();
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.PERFORMANCE_BOND, bondId,
                "Performance-BG-Scan.pdf", ContractAttachmentType.BG_SCAN, "Performance Bank Guarantee 10% — SBI");
        // Variation order level (we'll synthesise a UUID since the VO rows live in 11-variation-orders.sql which uses gen_random_uuid)
        UUID voId = UUID.randomUUID();
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.VARIATION_ORDER, voId,
                "VO-001-IIT-Roorkee-Peer-Review.pdf", ContractAttachmentType.TEST_REPORT, "VO-001 supporting peer-review report");
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.VARIATION_ORDER, voId,
                "VO-001-Approval-Note.pdf", ContractAttachmentType.CERTIFICATE, "VO-001 NHAI RO Jaipur approval note");
        // Milestone level — synthesise milestone ids
        UUID milestone1 = UUID.randomUUID();
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.MILESTONE, milestone1,
                "MoM-Earthwork-Handover.pdf", ContractAttachmentType.MOM, "Earthwork milestone — handover MoM");
        UUID milestone2 = UUID.randomUUID();
        created += persistContractAttachment(project.getId(), main, AttachmentEntityType.MILESTONE, milestone2,
                "MB-Bridge-Pier-P2.pdf", ContractAttachmentType.MEASUREMENT_BOOK, "Measurement Book — Bridge Pier P2 cast");

        log.info("[NH-48 attachments] seeded {} contract attachments across CONTRACT/MILESTONE/VO/BOND levels", created);
    }

    private int persistContractAttachment(UUID projectId, Contract contract, AttachmentEntityType entityType, UUID entityId,
                                          String fileName, ContractAttachmentType attachmentType, String description) {
        byte[] bytes = MinimalPdfGenerator.render(
                fileName.replace(".pdf", ""),
                description,
                attachmentType.name(),
                contract.getContractNumber(),
                "NH-48 Rajasthan",
                contract.getContractorName(),
                "Auto-generated contract attachment");
        UUID contentId = UUID.randomUUID();
        String relativePath = projectId + "/" + contract.getId() + "/" + contentId + "/" + fileName;
        writeBinary(CONTRACT_STORAGE_ROOT.resolve(relativePath), bytes);

        ContractAttachment att = new ContractAttachment();
        att.setProjectId(projectId);
        att.setContractId(contract.getId());
        att.setEntityType(entityType);
        att.setEntityId(entityId);
        att.setFileName(fileName);
        att.setFileSize((long) bytes.length);
        att.setMimeType("application/pdf");
        att.setFilePath(relativePath);
        att.setAttachmentType(attachmentType);
        att.setDescription(description);
        att.setUploadedBy("seeder");
        att.setUploadedAt(Instant.now());
        contractAttachmentRepository.save(att);
        return 1;
    }

    // ────────────────────────── GIS polygons ──────────────────────────

    private record SeededPolygon(WbsPolygon polygon, WbsNode wbsNode) {}

    private List<SeededPolygon> seedGisPolygons(Project project) {
        // GIS layer (one per project)
        GisLayer layer = new GisLayer();
        layer.setProjectId(project.getId());
        layer.setLayerName("NH-48 WBS Boundaries");
        layer.setLayerType(GisLayerType.WBS_POLYGON);
        layer.setDescription("Synthetic corridor polygons along NH-48 Rajasthan (Ch 145+000 to Ch 165+000) — demo data");
        layer.setIsVisible(true);
        layer.setOpacity(0.7);
        layer.setSortOrder(10);
        UUID layerId = gisLayerRepository.save(layer).getId();

        // L2 WBS nodes (level 2 only; root is level 1)
        List<WbsNode> l2Nodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(project.getId()).stream()
                .filter(n -> Integer.valueOf(2).equals(n.getWbsLevel()))
                .toList();
        if (l2Nodes.isEmpty()) {
            log.warn("[NH-48 attachments] no L2 WBS nodes — GIS polygon seeding skipped");
            return List.of();
        }

        var segments = CorridorGeometry.buildSegments(
                NH48_START_LAT, NH48_START_LON, NH48_END_LAT, NH48_END_LON,
                l2Nodes.size(), CORRIDOR_HALF_WIDTH_M);

        List<SeededPolygon> out = new java.util.ArrayList<>(l2Nodes.size());
        for (int i = 0; i < l2Nodes.size(); i++) {
            WbsNode node = l2Nodes.get(i);
            CorridorGeometry.Segment seg = segments.get(i);
            WbsPolygon p = new WbsPolygon();
            p.setProjectId(project.getId());
            p.setWbsNodeId(node.getId());
            p.setLayerId(layerId);
            p.setWbsCode(node.getCode());
            p.setWbsName(node.getName());
            p.setPolygon(seg.polygon());
            p.setCenterLatitude(seg.centerLat());
            p.setCenterLongitude(seg.centerLon());
            p.setAreaInSqMeters(seg.areaSqMetres());
            p.setFillColor(packageColor(i));
            p.setStrokeColor("#000000");
            WbsPolygon saved = wbsPolygonRepository.save(p);
            out.add(new SeededPolygon(saved, node));
        }
        log.info("[NH-48 attachments] seeded {} WBS polygons along NH-48 corridor (layer={})", out.size(), layerId);
        return out;
    }

    private String packageColor(int index) {
        String[] palette = {"#e6194b", "#3cb44b", "#ffe119", "#4363d8", "#f58231", "#911eb4", "#46f0f0"};
        return palette[index % palette.length];
    }

    // ─────────────────────── Satellite + progress ──────────────────────

    private void seedSatelliteAndProgress(Project project, List<SeededPolygon> polygons) {
        if (polygons.isEmpty()) {
            log.warn("[NH-48 attachments] no polygons — satellite seeding skipped");
            return;
        }
        // Compute bbox directly from saved JTS polygons (no Segment round-trip needed).
        double bboxW = Double.POSITIVE_INFINITY, bboxS = Double.POSITIVE_INFINITY;
        double bboxE = Double.NEGATIVE_INFINITY, bboxN = Double.NEGATIVE_INFINITY;
        for (SeededPolygon sp : polygons) {
            for (var c : sp.polygon().getPolygon().getExteriorRing().getCoordinates()) {
                if (c.x < bboxW) bboxW = c.x;
                if (c.x > bboxE) bboxE = c.x;
                if (c.y < bboxS) bboxS = c.y;
                if (c.y > bboxN) bboxN = c.y;
            }
        }
        double[] bbox = new double[] {bboxW, bboxS, bboxE, bboxN};

        record Scene(String code, LocalDate date, String filename, String label) {}
        List<Scene> scenes = List.of(
                new Scene("NH48-2025-Q1", LocalDate.of(2025, 2, 1), "nh48-q1-2025.jpeg", "Pre-monsoon site mobilisation"),
                new Scene("NH48-2025-Q2", LocalDate.of(2025, 5, 1), "nh48-q2-2025.jpeg", "Earthwork in progress"),
                new Scene("NH48-2025-Q3", LocalDate.of(2025, 8, 1), "nh48-q3-2025.jpeg", "Post-monsoon resumption"),
                new Scene("NH48-2025-Q4", LocalDate.of(2025, 11, 1), "nh48-q4-2025.jpeg", "Sub-base + bituminous active"),
                new Scene("NH48-2026-Q1", LocalDate.of(2026, 2, 1), "nh48-q1-2026.jpeg", "Bituminous and structures progressing")
        );

        int sceneCount = 0;
        int snapCount = 0;
        for (int sIdx = 0; sIdx < scenes.size(); sIdx++) {
            Scene s = scenes.get(sIdx);
            byte[] imageBytes = loadBinary(SATELLITE_RESOURCE_ROOT + s.filename()).orElse(null);
            if (imageBytes == null) {
                log.warn("[NH-48 attachments] satellite image missing on classpath: {}", s.filename());
                continue;
            }
            UUID contentId = UUID.randomUUID();
            String storageKey = project.getId() + "/" + contentId + "/" + s.filename();
            URI storedAt = rasterStorage.put(storageKey, imageBytes, "image/jpeg");

            SatelliteImage img = new SatelliteImage();
            img.setProjectId(project.getId());
            img.setSceneId(s.code());
            img.setImageName("NH-48 " + s.label() + " (" + s.code() + ")");
            img.setDescription("Quarterly site survey — " + s.label() + ". Source: project field survey, treated as satellite imagery for the GIS demo.");
            img.setCaptureDate(s.date());
            img.setSource(SatelliteImageSource.MANUAL_UPLOAD);
            img.setResolution("3840x2160 (UHD field photo)");
            img.setBoundingBoxGeoJson(CorridorGeometry.bboxAsGeoJson(bbox));
            img.setFilePath(storedAt.toString());
            img.setFileSize((long) imageBytes.length);
            img.setMimeType("image/jpeg");
            img.setNorthBound(bbox[3]);
            img.setSouthBound(bbox[1]);
            img.setEastBound(bbox[2]);
            img.setWestBound(bbox[0]);
            img.setStatus(SatelliteImageStatus.READY);
            img.setCloudCoverPercent(deterministicCloud(s.code()));
            SatelliteImage savedImg = satelliteImageRepository.save(img);
            sceneCount++;

            // One snapshot per L2 polygon for this scene — progress interpolates from 0 to a
            // package-specific final %.
            for (SeededPolygon p : polygons) {
                ConstructionProgressSnapshot snap = new ConstructionProgressSnapshot();
                snap.setProjectId(project.getId());
                snap.setWbsPolygonId(p.polygon().getId());
                snap.setCaptureDate(s.date());
                snap.setSatelliteImageId(savedImg.getId());
                double finalPct = packageFinalPercent(p.wbsNode().getCode());
                double progress = finalPct * (sIdx + 1) / scenes.size();
                snap.setDerivedProgressPercent(progress);
                snap.setContractorClaimedPercent(progress + 1.5);
                snap.setVariancePercent(-1.5);
                snap.setAiProgressPercent(progress);
                snap.setCvi((double) (40 + 10 * sIdx));
                snap.setEdi((double) (30 + 12 * sIdx));
                snap.setNdviChange(-0.05 - 0.01 * sIdx);
                snap.setWbsPackageCode(p.wbsNode().getCode());
                snap.setAlertFlag(sIdx == 0 ? SatelliteAlertFlag.GREEN : SatelliteAlertFlag.AMBER_VARIANCE_GT5);
                snap.setAnalysisMethod(ProgressAnalysisMethod.MANUAL);
                snap.setAnalyzerId("seeder:nh48-attachments-1.0");
                snap.setAnalysisDurationMs(0);
                snap.setAnalysisCostMicros(0L);
                snap.setRemarks("Synthetic snapshot — interpolated from package final %.");
                progressSnapshotRepository.save(snap);
                snapCount++;
            }
        }
        log.info("[NH-48 attachments] seeded {} satellite scenes + {} construction progress snapshots", sceneCount, snapCount);
    }

    /** Deterministic 0–15 % cloud cover from sceneId hash — keeps snapshots reproducible. */
    private double deterministicCloud(String sceneId) {
        return Math.abs(sceneId.hashCode() % 15);
    }

    /** Final percent-complete per WBS package as of today's data date — drives progress snapshot interpolation. */
    private double packageFinalPercent(String wbsCode) {
        return switch (wbsCode) {
            case "WBS-1" -> 95.0;  // Earthwork
            case "WBS-2" -> 90.0;  // Sub-base
            case "WBS-3" -> 80.0;  // Bituminous
            case "WBS-4" -> 75.0;  // Drainage
            case "WBS-5" -> 25.0;  // Road furniture
            case "WBS-6" -> 85.0;  // Bridges
            case "WBS-7" -> 10.0;  // Misc
            default -> 50.0;
        };
    }

    // ─────────────────────────── Helpers ──────────────────────────

    private Optional<byte[]> loadBinary(String classpathPath) {
        ClassPathResource res = new ClassPathResource(classpathPath);
        if (!res.exists()) return Optional.empty();
        try (InputStream in = res.getInputStream()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            log.warn("[NH-48 attachments] failed to load {}: {}", classpathPath, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeBinary(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write binary to " + target, e);
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/\\x00]", "_");
    }
}
