package com.bipros.api.config.seeder;

import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiRegister;
import com.bipros.document.domain.model.RfiStatus;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.RfiRegisterRepository;
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
 * IC-PMS RFI register seeder — projects {@link DocumentType#RFI} documents seeded by
 * {@link IcpmsPhaseDSeeder} into the specialised {@code document.rfi_registers} table
 * so Module 6 RFI screens have rows. Closes the RFI half of the Module 6 gap.
 *
 * <p>Strategy: existing RFI docs get raisedBy=EPC, assignedTo=PMC; status mirrors doc
 * status (CLOSED→CLOSED + closedDate, else OPEN). Priority is rolled by RFI subject
 * heuristics (reinforcement/soil → HIGH, access/alignment → MEDIUM).
 *
 * <p>Sentinel: existing rows in {@code rfi_registers} → skip.
 */
@Slf4j
@Component
@Profile("dev")
@Order(114)
@RequiredArgsConstructor
public class IcpmsRfiRegisterSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final RfiRegisterRepository rfiRegisterRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (rfiRegisterRepository.count() > 0) {
            log.info("[IC-PMS RFI Register] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS RFI Register] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        List<Document> rfiDocs = documentRepository.findByProjectId(projectId).stream()
            .filter(d -> d.getDocumentType() == DocumentType.RFI
                || (d.getDocumentNumber() != null && d.getDocumentNumber().startsWith("RFI-")))
            .toList();

        if (rfiDocs.isEmpty()) {
            log.warn("[IC-PMS RFI Register] no RFI-type documents found — run Phase D first");
            return;
        }

        int seeded = 0;
        for (Document doc : rfiDocs) {
            RfiRegister rfi = new RfiRegister();
            rfi.setProjectId(projectId);
            rfi.setRfiNumber(doc.getDocumentNumber());
            rfi.setSubject(doc.getTitle());
            rfi.setDescription(buildDescription(doc));
            rfi.setRaisedBy("L&T IDPL");           // EPC contractor short-name
            rfi.setAssignedTo("AECOM-TYPSA");      // PMC short-name
            LocalDate raised = resolveRaisedDate(doc);
            rfi.setRaisedDate(raised);
            rfi.setDueDate(raised.plusDays(14));   // standard 14-day response SLA
            RfiStatus mappedStatus = mapStatus(doc.getStatus());
            rfi.setStatus(mappedStatus);
            if (mappedStatus == RfiStatus.CLOSED) {
                rfi.setClosedDate(raised.plusDays(10));
                rfi.setResponse("Clarification issued by PMC via formal correspondence. "
                    + "Refer to transmittal register for revised drawing issue.");
            }
            rfi.setPriority(derivePriority(doc.getTitle()));
            rfiRegisterRepository.save(rfi);
            seeded++;
        }

        log.info("[IC-PMS RFI Register] seeded {} rows from {} RFI docs", seeded, rfiDocs.size());
    }

    private String buildDescription(Document doc) {
        return "Formal Request for Information on \"" + doc.getTitle()
            + "\" raised against WBS package "
            + (doc.getWbsPackageCode() != null ? doc.getWbsPackageCode() : "DMIC-PROG")
            + ". Contractor requests clarification to proceed with work execution.";
    }

    private LocalDate resolveRaisedDate(Document doc) {
        if (doc.getIssuedDate() != null) return doc.getIssuedDate();
        if (doc.getCreatedAt() != null) {
            return doc.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.now();
    }

    /** CLOSED doc → CLOSED RFI, OPEN doc → OPEN RFI, everything else → OPEN. */
    private RfiStatus mapStatus(DocumentStatus status) {
        if (status == null) return RfiStatus.OPEN;
        return switch (status) {
            case CLOSED -> RfiStatus.CLOSED;
            case OPEN, DRAFT, UNDER_REVIEW -> RfiStatus.OPEN;
            default -> RfiStatus.OPEN;
        };
    }

    /** Roll priority by keyword in the RFI subject line. */
    private RfiPriority derivePriority(String subject) {
        if (subject == null) return RfiPriority.MEDIUM;
        String s = subject.toLowerCase();
        if (s.contains("rebar") || s.contains("soil") || s.contains("structural")) {
            return RfiPriority.HIGH;
        }
        if (s.contains("access") || s.contains("alignment") || s.contains("tender")) {
            return RfiPriority.MEDIUM;
        }
        return RfiPriority.LOW;
    }
}
