package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.Organisation;
import com.bipros.admin.domain.repository.OrganisationRepository;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.Transmittal;
import com.bipros.document.domain.model.TransmittalItem;
import com.bipros.document.domain.model.TransmittalPurpose;
import com.bipros.document.domain.model.TransmittalStatus;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.TransmittalItemRepository;
import com.bipros.document.domain.repository.TransmittalRepository;
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
import java.util.List;
import java.util.UUID;

/**
 * IC-PMS transmittal seeder — populates {@code document.transmittals} and
 * {@code document.transmittal_items} with 3 representative transmittals bundling the
 * drawings/specs seeded by {@link IcpmsPhaseDSeeder} to their receiving organisations.
 *
 * <p>Bundles:
 * <ol>
 *   <li>{@code TRN-N03-P01-001} — AECOM-TYPSA → DIICDC, IFC drawing package (SENT)</li>
 *   <li>{@code TRN-N04-P01-001} — Egis → DMICDC, specifications issue (ACKNOWLEDGED)</li>
 *   <li>{@code TRN-N05-P01-001} — Mott MacDonald → SIPCOT, design transmittal (SENT/OVERDUE)</li>
 * </ol>
 *
 * <p>Sentinel: existing rows in {@code transmittals} → skip.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(115)
@RequiredArgsConstructor
public class IcpmsTransmittalSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final TransmittalRepository transmittalRepository;
    private final TransmittalItemRepository transmittalItemRepository;
    private final OrganisationRepository organisationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (transmittalRepository.count() > 0) {
            log.info("[IC-PMS Transmittal] already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Transmittal] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        String aecom = resolveShortName("AECOM-TYPSA", "AECOM-TYPSA");
        String diicdc = resolveShortName("DIICDC", "DIICDC");
        String egis = resolveShortName("EGIS-PMC", "Egis");
        String dmicdc = resolveShortName("DMICDC", "DMICDC");
        String mott = resolveShortName("MOTT-MAC", "Mott MacDonald");
        String sipcot = resolveShortName("SIPCOT", "SIPCOT");

        int items = 0;

        // 1. TRN-N03-P01-001 — AECOM-TYPSA → DIICDC — IFC drawing package issue
        Transmittal t1 = saveTransmittal(projectId,
            "TRN-N03-P01-001",
            "DMIC-N03-P01 IFC Drawing Package Issue",
            aecom, diicdc,
            LocalDate.of(2025, 11, 15),
            LocalDate.of(2025, 12, 15),
            TransmittalStatus.SENT,
            "IFC package for DMIC-N03-P01 activation kiln. 6 drawings released to Employer SPV for construction.");
        items += addItems(t1.getId(),
            List.of("DRW-N03-P01-001", "DRW-N03-P01-002", "DRW-N03-P01-003",
                    "DRW-N03-P01-004", "DRW-N03-P01-005", "DRW-N03-P02-001"),
            TransmittalPurpose.FOR_CONSTRUCTION);

        // 2. TRN-N04-P01-001 — Egis → DMICDC — specifications issue (acknowledged)
        Transmittal t2 = saveTransmittal(projectId,
            "TRN-N04-P01-001",
            "DMIC-N04 Specifications Issue",
            egis, dmicdc,
            LocalDate.of(2026, 1, 20),
            LocalDate.of(2026, 2, 19),
            TransmittalStatus.ACKNOWLEDGED,
            "Consolidated specifications pack for DMIC-N04 logistics park and road-network works.");
        items += addItems(t2.getId(),
            List.of("SPEC-CIVIL-001", "SPEC-ELEC-001", "SPEC-ICT-001", "SPEC-HVAC-001"),
            TransmittalPurpose.FOR_APPROVAL);

        // 3. TRN-N05-P01-001 — Mott MacDonald → SIPCOT — design transmittal (overdue risk)
        Transmittal t3 = saveTransmittal(projectId,
            "TRN-N05-P01-001",
            "DMIC-N05 Design Transmittal",
            mott, sipcot,
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 3, 12),           // before data_date (2026-04-15) → will read as OVERDUE downstream
            TransmittalStatus.SENT,
            "Design drawings for DMIC-N05-P01 multi-modal terminal issued to SIPCOT for review and comment.");
        items += addItems(t3.getId(),
            List.of("DRW-N05-P01-001", "DRW-N04-P01-001", "DRW-N04-P02-001"),
            TransmittalPurpose.FOR_REVIEW);

        log.info("[IC-PMS Transmittal] seeded 3 transmittals with {} items total", items);
    }

    private String resolveShortName(String code, String fallback) {
        return organisationRepository.findByCode(code)
            .map(Organisation::getShortName)
            .orElse(fallback);
    }

    private Transmittal saveTransmittal(UUID projectId, String number, String subject,
                                        String fromParty, String toParty,
                                        LocalDate sent, LocalDate due,
                                        TransmittalStatus status, String remarks) {
        Transmittal t = new Transmittal();
        t.setProjectId(projectId);
        t.setTransmittalNumber(number);
        t.setSubject(subject);
        t.setFromParty(fromParty);
        t.setToParty(toParty);
        t.setSentDate(sent);
        t.setDueDate(due);
        t.setStatus(status);
        t.setRemarks(remarks);
        return transmittalRepository.save(t);
    }

    /**
     * Look up each document by its canonical number and create a transmittal item
     * if found. Missing doc numbers are logged and skipped (a defensive posture —
     * Phase D seeds them all, but we don't want this seeder to hard-fail on a typo).
     */
    private int addItems(UUID transmittalId, List<String> docNumbers, TransmittalPurpose purpose) {
        int created = 0;
        for (String docNumber : docNumbers) {
            Document doc = documentRepository.findByDocumentNumber(docNumber).orElse(null);
            if (doc == null) {
                log.warn("[IC-PMS Transmittal] document {} not found — skipping item", docNumber);
                continue;
            }
            TransmittalItem item = new TransmittalItem();
            item.setTransmittalId(transmittalId);
            item.setDocumentId(doc.getId());
            item.setPurpose(purpose);
            item.setRemarks("Revision R" + (doc.getCurrentVersion() == null ? 1 : doc.getCurrentVersion()));
            transmittalItemRepository.save(item);
            created++;
        }
        return created;
    }
}
