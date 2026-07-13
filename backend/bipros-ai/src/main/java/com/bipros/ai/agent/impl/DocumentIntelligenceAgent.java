package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.EvidenceRef;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.service.DocumentService;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.repository.PermitRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Document Intelligence agent (#? in the fleet). Deterministic {@link #gather} that reads the
 * project document register ({@link DocumentService}) and permit-to-work records
 * ({@link PermitRepository}) and emits fully templated {@link AgentFindingDraft}s the LLM narrator
 * may only reword.
 *
 * <p>Findings:
 * <ul>
 *   <li>{@code PERMIT_EXPIRY} — live permits whose end date falls within the next
 *       {@value #PERMIT_WINDOW_DAYS} days for the project.</li>
 *   <li>{@code DOCUMENT_SUMMARY} — count/type of documents registered in the last
 *       {@value #RECENT_DAYS} days.</li>
 *   <li>{@code COMPLIANCE_DOC_GAP} — an expected core document type with zero documents on an
 *       otherwise-populated register.</li>
 * </ul>
 * All numbers are direct counts from the domain, so confidence is high and stated as such.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIntelligenceAgent extends AbstractAgent {

    private static final String KEY = "document_intelligence";
    private static final Duration TTL = Duration.ofDays(7);

    /** Documents registered within this many days count as "recent". */
    private static final int RECENT_DAYS = 7;
    /** Permits whose end date is within this many days are flagged as expiring. */
    private static final int PERMIT_WINDOW_DAYS = 7;
    /** Upper bound on permits scanned per project run (headroom well beyond any real project's permit count). */
    private static final int MAX_PERMITS = 5000;

    /** Permit states that represent a live, in-force permit — matches the Permit dashboard
     *  ({@code PermitDashboardService}) / expiry job, which count ISSUED + IN_PROGRESS only. */
    private static final Set<PermitStatus> LIVE_STATUSES =
            EnumSet.of(PermitStatus.ISSUED, PermitStatus.IN_PROGRESS);

    /** Core document types a controlled project register is expected to hold. */
    private static final List<DocumentType> EXPECTED_TYPES =
            List.of(DocumentType.DRAWING, DocumentType.SPECIFICATION, DocumentType.CONTRACT_DOCUMENT);

    private final DocumentService documentService;
    private final PermitRepository permitRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Document Intelligence";
    }

    @Override
    public boolean supportsPortfolio() {
        return false;
    }

    @Override
    public GatherResult gather(AgentRunContext ctx) {
        UUID projectId = ctx.projectId();
        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        Instant validUntil = now.plus(TTL);

        // ---------------------------------------------------------------- documents
        List<DocumentResponse> docs = documentService.listDocuments(projectId);
        if (docs == null) {
            docs = List.of();
        }
        int totalDocs = docs.size();
        Instant recentCutoff = now.minus(Duration.ofDays(RECENT_DAYS));
        Set<DocumentType> presentTypes = EnumSet.noneOf(DocumentType.class);
        Map<String, Integer> recentByType = new TreeMap<>();
        int recentDocs = 0;
        for (DocumentResponse d : docs) {
            if (d.documentType() != null) {
                presentTypes.add(d.documentType());
            }
            if (d.createdAt() != null && !d.createdAt().isBefore(recentCutoff)) {
                recentDocs++;
                String type = d.documentType() == null ? "UNSPECIFIED" : d.documentType().name();
                recentByType.merge(type, 1, Integer::sum);
            }
        }
        List<String> missingTypes = new ArrayList<>();
        for (DocumentType expected : EXPECTED_TYPES) {
            if (!presentTypes.contains(expected)) {
                missingTypes.add(expected.name());
            }
        }

        // ------------------------------------------------------------------ permits
        List<Permit> permits = permitRepository
                .findByProjectIdRecent(projectId, PageRequest.of(0, MAX_PERMITS))
                .getContent();
        Instant windowEnd = now.plus(Duration.ofDays(PERMIT_WINDOW_DAYS));
        List<Permit> expiring = new ArrayList<>();
        for (Permit p : permits) {
            if (!LIVE_STATUSES.contains(p.getStatus())) {
                continue;
            }
            Instant end = p.getEndAt();
            if (end == null || end.isBefore(now) || end.isAfter(windowEnd)) {
                continue;
            }
            expiring.add(p);
        }
        expiring.sort(Comparator.comparing(Permit::getEndAt));

        // --------------------------------------------------------------- snapshot
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("totalDocuments", totalDocs);
        snapshot.put("recentDocuments", recentDocs);
        ObjectNode byType = snapshot.putObject("recentByType");
        recentByType.forEach((type, count) -> byType.put(type, count));
        ArrayNode missing = snapshot.putArray("missingExpectedTypes");
        missingTypes.forEach(missing::add);
        snapshot.put("expiringPermits", expiring.size());

        // --------------------------------------------------------------- findings
        List<AgentFindingDraft> candidates = new ArrayList<>();
        if (!expiring.isEmpty()) {
            candidates.add(permitExpiry(projectId, expiring, now, validUntil));
        }
        if (recentDocs > 0) {
            candidates.add(documentSummary(projectId, totalDocs, recentDocs, recentByType, validUntil));
        }
        if (totalDocs > 0 && !missingTypes.isEmpty()) {
            candidates.add(complianceGap(projectId, totalDocs, missingTypes, validUntil));
        }

        candidates.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        return new GatherResult(snapshot, candidates);
    }

    private AgentFindingDraft permitExpiry(
            UUID projectId, List<Permit> expiring, Instant now, Instant validUntil) {
        Permit soonest = expiring.get(0);
        long days = Math.max(0, Duration.between(now, soonest.getEndAt()).toDays());
        int n = expiring.size();
        Severity severity = days <= 1 ? Severity.CRITICAL : days <= 3 ? Severity.HIGH : Severity.MEDIUM;

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Permits expiring", n + " within " + PERMIT_WINDOW_DAYS + " days"));
        evidence.add(EvidenceRef.metric("Earliest expiry",
                soonest.getPermitCode() + " in " + days + " day(s)"));
        for (Permit p : expiring.subList(0, Math.min(3, n))) {
            evidence.add(EvidenceRef.entity("Permit", p.getPermitCode(), "permit", p.getId(),
                    "/projects/" + projectId + "/permits?focus=" + p.getId()));
        }

        return new AgentFindingDraft(
                "PERMIT_EXPIRY",
                "PROJECT",
                severity,
                0.95,
                "Direct count of live permits with an end date within the next " + PERMIT_WINDOW_DAYS + " days",
                n + " permit(s) expire within " + PERMIT_WINDOW_DAYS + " days",
                n + " live permit(s) reach their end date within the next " + PERMIT_WINDOW_DAYS
                        + " days; the earliest is " + soonest.getPermitCode() + " in " + days + " day(s).",
                "Permit-to-work windows are time-boxed; work under each permit must stop or the permit "
                        + "must be renewed before its end date.",
                "Working past a permit's expiry is a safety-compliance breach that can trigger a stop-work "
                        + "order, and letting a permit lapse forces re-approval that delays the affected activities.",
                "Review the expiring permits and initiate renewal or close-out before their end dates; confirm "
                        + "the work is complete or re-scope the permit window.",
                evidence,
                Map.of("SITE_MANAGER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft documentSummary(
            UUID projectId, int totalDocs, int recentDocs, Map<String, Integer> recentByType, Instant validUntil) {
        int typeCount = recentByType.size();
        String typeList = String.join(", ", recentByType.keySet());

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("New documents (" + RECENT_DAYS + "d)", String.valueOf(recentDocs)));
        evidence.add(EvidenceRef.metric("Register total", String.valueOf(totalDocs)));
        recentByType.forEach((type, count) ->
                evidence.add(EvidenceRef.metric(type, String.valueOf(count))));

        return new AgentFindingDraft(
                "DOCUMENT_SUMMARY",
                "PROJECT",
                Severity.INFO,
                0.90,
                "Direct count of documents created in the last " + RECENT_DAYS + " days",
                recentDocs + " document(s) added in the last " + RECENT_DAYS + " days",
                recentDocs + " new document(s) were registered in the last " + RECENT_DAYS + " days across "
                        + typeCount + " type(s) (" + typeList + "). The register now holds " + totalDocs
                        + " document(s) in total.",
                "Ongoing design issue, transmittals and site correspondence add documents to the register "
                        + "as the project progresses.",
                "A steady document inflow keeps the register current, but each new revision needs review and "
                        + "distribution to the right disciplines to stay controlled.",
                "Verify the new documents are filed under the correct type and folder and that any superseded "
                        + "revisions have been marked accordingly.",
                evidence,
                Map.of("DOCUMENT_CONTROLLER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }

    private AgentFindingDraft complianceGap(
            UUID projectId, int totalDocs, List<String> missingTypes, Instant validUntil) {
        String missingList = String.join(", ", missingTypes);

        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(EvidenceRef.metric("Missing types", missingList));
        evidence.add(EvidenceRef.metric("Register total", String.valueOf(totalDocs)));

        return new AgentFindingDraft(
                "COMPLIANCE_DOC_GAP",
                "PROJECT",
                Severity.MEDIUM,
                0.90,
                "Expected core document types checked against the project register",
                "Missing expected document type(s): " + missingList,
                "The register holds " + totalDocs + " document(s) but has no document of type(s) "
                        + missingList + ".",
                "These document types have not yet been uploaded, or existing documents were filed under a "
                        + "different type.",
                "Missing core documents (drawings, specifications or contract) leave the delivery team without "
                        + "the controlled reference set and are a common audit and handover finding.",
                "Confirm whether these documents exist and upload them under the correct type, or record why "
                        + "they are not applicable to this project.",
                evidence,
                Map.of("DOCUMENT_CONTROLLER", List.of(), "PROJECT_MANAGER", List.of()),
                validUntil);
    }
}
