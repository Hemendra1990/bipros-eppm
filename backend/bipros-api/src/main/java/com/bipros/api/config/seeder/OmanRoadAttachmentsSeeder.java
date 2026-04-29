package com.bipros.api.config.seeder;

import com.bipros.api.config.seeder.util.MinimalPdfGenerator;
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
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Oman Barka–Nakhal Road project — fills the document register and 5 placeholder satellite
 * JPEGs that the main {@link OmanRoadProjectSeeder} (Order 141) leaves blank.
 *
 * <p>Sentinel: project lookup by code {@code 6155}. Whole seeder no-ops if any documents
 * already exist for the project.
 *
 * <p>Generates 10 stub PDFs at runtime via {@link MinimalPdfGenerator}. The 5 satellite
 * JPEGs ship on the classpath under {@code seed-data/oman-road-project/satellite/}; the
 * actual GIS layer / WBS polygon / SatelliteImage rows are seeded by
 * {@link OmanRoadProjectSupplementalSeeder} (Order 151). This seeder only ensures the
 * binaries exist on disk + the document register has entries.
 */
@Slf4j
@Component
@Profile("seed")
@Order(146)
@RequiredArgsConstructor
public class OmanRoadAttachmentsSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final Path DOCUMENT_STORAGE_ROOT =
            Paths.get("./storage/documents").toAbsolutePath().normalize();

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFolderRepository documentFolderRepository;
    private final DocumentVersionRepository documentVersionRepository;

    @Override
    public void run(String... args) {
        Optional<Project> opt = projectRepository.findByCode(PROJECT_CODE);
        if (opt.isEmpty()) {
            log.warn("[BNK-ATTACH] project '{}' not found — skipping (run OmanRoadProjectSeeder first)",
                    PROJECT_CODE);
            return;
        }
        Project project = opt.get();
        long existing = documentRepository.findByProjectId(project.getId()).size();
        if (existing > 0) {
            log.info("[BNK-ATTACH] project '{}' already has {} documents — skipping",
                    PROJECT_CODE, existing);
            return;
        }

        log.info("[BNK-ATTACH] seeding documents (~10 PDFs via MinimalPdfGenerator) for '{}'",
                PROJECT_CODE);

        Folders folders = ensureFolders(project.getId());
        seedDocuments(project, folders);

        log.info("[BNK-ATTACH] done.");
    }

    // ────────────────────────── Folders ──────────────────────────

    private record Folders(UUID methodStatements, UUID itps, UUID hsePlans, UUID qualityPlans,
                           UUID generalPlans, UUID boqDocs) {}

    private Folders ensureFolders(UUID projectId) {
        return new Folders(
                ensureFolder(projectId, "BNK-MS", "Method Statements", DocumentCategory.GENERAL, 10),
                ensureFolder(projectId, "BNK-ITP", "Inspection & Test Plans", DocumentCategory.GENERAL, 20),
                ensureFolder(projectId, "BNK-HSE", "HSE Plans", DocumentCategory.GENERAL, 30),
                ensureFolder(projectId, "BNK-QP", "Quality Plans", DocumentCategory.GENERAL, 40),
                ensureFolder(projectId, "BNK-PLAN", "Project Plans", DocumentCategory.GENERAL, 50),
                ensureFolder(projectId, "BNK-BOQ", "BOQ Documents", DocumentCategory.GENERAL, 60));
    }

    private UUID ensureFolder(UUID projectId, String code, String name,
                              DocumentCategory category, int sortOrder) {
        return documentFolderRepository.findByProjectIdOrderBySortOrder(projectId).stream()
                .filter(f -> code.equals(f.getCode()))
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

    /** Single register entry — drives both Document insertion and PDF generation. */
    private record DocEntry(
            String docNumber,
            String title,
            String category,
            String specRef,
            String issuedBy,
            String approvedBy,
            DocumentStatus status,
            DocumentType type,
            DrawingDiscipline discipline,
            String folderKey) {}

    private void seedDocuments(Project project, Folders folders) {
        List<DocEntry> entries = registerEntries();
        int seeded = 0;
        for (DocEntry e : entries) {
            UUID folderId = switch (e.folderKey()) {
                case "MS" -> folders.methodStatements();
                case "ITP" -> folders.itps();
                case "HSE" -> folders.hsePlans();
                case "QP" -> folders.qualityPlans();
                case "PLAN" -> folders.generalPlans();
                case "BOQ" -> folders.boqDocs();
                default -> folders.generalPlans();
            };

            byte[] binary = MinimalPdfGenerator.render(
                    e.docNumber(), e.title(), e.category(), e.specRef(),
                    project.getName(), e.approvedBy(),
                    "Sample seed data — Oman Barka–Nakhal Road");
            String fileName = sanitizeFileName(e.docNumber() + ".pdf");

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
            doc.setMimeType("application/pdf");
            doc.setFilePath(relativePath);
            doc.setCurrentVersion(1);
            doc.setStatus(e.status());
            doc.setDocumentType(e.type());
            doc.setDiscipline(e.discipline());
            doc.setIssuedBy(e.issuedBy());
            doc.setIssuedDate(LocalDate.of(2024, 9, 15));
            if (e.status() == DocumentStatus.APPROVED) {
                doc.setApprovedBy(e.approvedBy());
                doc.setApprovedDate(LocalDate.of(2024, 10, 1));
            }
            doc.setTags("oman,barka-nakhal,bnk," + e.category().toLowerCase().replace(' ', '-'));
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
        log.info("[BNK-ATTACH] seeded {} documents (folders: MS/ITP/HSE/QP/PLAN/BOQ)", seeded);
    }

    /**
     * 10 register entries per plan §4.35 — most APPROVED, a few UNDER_REVIEW, 1 DRAFT
     * (operability — user can review/approve them).
     */
    private List<DocEntry> registerEntries() {
        return List.of(
                new DocEntry("BNK-MS-EARTH", "Method Statement — Earthworks",
                        "Method Statement", "MoTC Spec Sec 305",
                        "Site Engineer", "PMC", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "MS"),
                new DocEntry("BNK-MS-PAVE", "Method Statement — Pavement (Bituminous Layers)",
                        "Method Statement", "MoTC Spec Sec 504",
                        "Site Engineer", "PMC", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "MS"),
                new DocEntry("BNK-ITP-BC", "ITP — Bituminous Concrete (BC) Wearing Course",
                        "Inspection & Test Plan", "MoTC Spec Sec 507",
                        "QC Engineer", "PMC", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "ITP"),
                new DocEntry("BNK-ITP-C30", "ITP — Concrete C30/37 Structures",
                        "Inspection & Test Plan", "BS EN 206 / IS 456",
                        "QC Engineer", "PMC", DocumentStatus.UNDER_REVIEW,
                        DocumentType.REPORT, null, "ITP"),
                new DocEntry("BNK-HSE-001", "HSE Plan — Site Health, Safety & Environment",
                        "HSE Plan", "ISO 45001 / OHA HSE Manual",
                        "HSE Manager", "PMC", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "HSE"),
                new DocEntry("BNK-QP-MAT", "Quality Plan — Materials Control",
                        "Quality Plan", "ISO 9001:2015",
                        "QC Manager", "PMC", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "QP"),
                new DocEntry("BNK-TMP-001", "Traffic Management Plan",
                        "Traffic Management Plan", "MoTC TMP Manual 2018",
                        "Traffic Engineer", "PMC", DocumentStatus.UNDER_REVIEW,
                        DocumentType.REPORT, null, "PLAN"),
                new DocEntry("BNK-EMP-001", "Environmental Mitigation Plan",
                        "Environmental Plan", "MECA EIA Decision 2023-117",
                        "Environment Officer", "MECA", DocumentStatus.APPROVED,
                        DocumentType.REPORT, null, "PLAN"),
                new DocEntry("BNK-PEP-001", "Project Execution Plan",
                        "Plan", "ISO 21500",
                        "Project Manager", "PMC", DocumentStatus.DRAFT,
                        DocumentType.REPORT, null, "PLAN"),
                new DocEntry("BNK-DOC-BOQ1", "BOQ Section 1 — Preliminaries (Priced)",
                        "BOQ Document", "MoTC BOQ Master 2024",
                        "QS Engineer", "PMC", DocumentStatus.APPROVED,
                        DocumentType.SPECIFICATION, null, "BOQ"));
    }

    // ─────────────────────────── Helpers ──────────────────────────

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
