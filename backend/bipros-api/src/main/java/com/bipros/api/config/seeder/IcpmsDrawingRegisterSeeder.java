package com.bipros.api.config.seeder;

import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingRegister;
import com.bipros.document.domain.model.DrawingStatus;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DrawingRegisterRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * IC-PMS drawing register seeder (GAP #8) — projects DRAWING-type documents seeded by
 * {@link IcpmsPhaseDSeeder} into the specialised {@code document.drawing_registers} table
 * so Module 6 drawing-register screens can pick them up.
 *
 * <p>One {@link DrawingRegister} row per existing drawing doc, inheriting its
 * number/title/discipline and denormalising revision/scale/package-code.
 *
 * <p>Sentinel: existing rows in {@code drawing_registers} → skip.
 */
@Slf4j
@Component
@Profile("dev")
@Order(113)
@RequiredArgsConstructor
public class IcpmsDrawingRegisterSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final DrawingRegisterRepository drawingRegisterRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (drawingRegisterRepository.count() > 0) {
            log.info("[IC-PMS Drawing Register] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Drawing Register] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        List<Document> drawings = documentRepository.findByProjectId(projectId).stream()
            .filter(d -> d.getDocumentType() == DocumentType.DRAWING)
            .toList();

        if (drawings.isEmpty()) {
            log.warn("[IC-PMS Drawing Register] no DRAWING-type documents found — run Phase D first");
            return;
        }

        int seeded = 0;
        for (Document doc : drawings) {
            DrawingRegister dr = new DrawingRegister();
            dr.setProjectId(projectId);
            dr.setDocumentId(doc.getId());
            dr.setDrawingNumber(doc.getDocumentNumber());
            dr.setTitle(doc.getTitle());
            dr.setDiscipline(doc.getDiscipline() != null
                ? doc.getDiscipline()
                : DrawingDiscipline.CIVIL);
            dr.setRevision("R" + (doc.getCurrentVersion() == null ? 1 : doc.getCurrentVersion()));
            dr.setRevisionDate(resolveRevisionDate(doc));
            dr.setStatus(mapStatus(doc.getStatus()));
            dr.setPackageCode(doc.getWbsPackageCode() != null
                ? doc.getWbsPackageCode()
                : deriveWbsPackage(doc.getDocumentNumber()));
            dr.setScale(deriveScale(dr.getDiscipline()));
            drawingRegisterRepository.save(dr);
            seeded++;
        }

        log.info("[IC-PMS Drawing Register] seeded {} rows from {} DRAWING docs",
            seeded, drawings.size());
    }

    /** Prefer the M6 issuedDate (IFC/IFA stamp); fall back to the row createdAt. */
    private LocalDate resolveRevisionDate(Document doc) {
        if (doc.getIssuedDate() != null) return doc.getIssuedDate();
        if (doc.getCreatedAt() != null) {
            return doc.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.now();
    }

    /**
     * Map {@link DocumentStatus} (richer set, used across all doc types) to the drawing
     * lifecycle. UNDER_REVIEW and DRAFT → PRELIMINARY; APPROVED/IFA → IFA;
     * IFC/PUBLISHED → IFC; SUPERSEDED → SUPERSEDED; all else → PRELIMINARY.
     */
    private DrawingStatus mapStatus(DocumentStatus status) {
        if (status == null) return DrawingStatus.PRELIMINARY;
        return switch (status) {
            case IFC, PUBLISHED -> DrawingStatus.IFC;
            case IFA, APPROVED -> DrawingStatus.IFA;
            case SUPERSEDED -> DrawingStatus.SUPERSEDED;
            case DRAFT, UNDER_REVIEW -> DrawingStatus.PRELIMINARY;
            default -> DrawingStatus.PRELIMINARY;
        };
    }

    /** Same heuristic as {@code IcpmsPhaseDSeeder#deriveWbsPackage}; fallback only. */
    private String deriveWbsPackage(String docNumber) {
        if (docNumber == null) return "DMIC-PROG";
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

    /** Sensible industry defaults per discipline (1:500 layouts, 1:50 architectural, etc.). */
    private String deriveScale(DrawingDiscipline discipline) {
        if (discipline == null) return "1:500";
        return switch (discipline) {
            case CIVIL -> "1:500";
            case STRUCTURAL -> "1:100";
            case ELECTRICAL -> "1:200";
            case MECHANICAL -> "1:100";
            case ARCHITECTURAL -> "1:50";
            case HVAC -> "1:100";
            case PLUMBING -> "1:100";
            case ICT -> "1:200";
            case MANAGEMENT, LEGAL, FINANCE -> "NTS";
        };
    }
}
