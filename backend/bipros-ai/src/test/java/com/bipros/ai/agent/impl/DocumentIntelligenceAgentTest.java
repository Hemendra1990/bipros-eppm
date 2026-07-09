package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.service.DocumentService;
import com.bipros.document.domain.model.DocumentStatus;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitStatus;
import com.bipros.permit.domain.repository.PermitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @Mock
    private DocumentService documentService;

    @Mock
    private PermitRepository permitRepository;

    private DocumentIntelligenceAgent agent() {
        return new DocumentIntelligenceAgent(documentService, permitRepository, new ObjectMapper());
    }

    private static DocumentResponse doc(DocumentType type, Instant createdAt) {
        return new DocumentResponse(
                UUID.randomUUID(), UUID.randomUUID(), PROJECT,
                (type == null ? "GEN-" : type.getCodePrefix()) + "001", "Title", null,
                "file.pdf", 1L, "application/pdf", "path", 1,
                DocumentStatus.DRAFT, type, null, null, null, null, null, null, null, null,
                createdAt, createdAt);
    }

    private static Permit permit(PermitStatus status, Instant endAt) {
        Permit p = new Permit();
        p.setId(UUID.randomUUID());
        p.setProjectId(PROJECT);
        p.setPermitCode("PTW-001");
        p.setStatus(status);
        p.setEndAt(endAt);
        return p;
    }

    @Test
    void emitsPermitDocumentAndComplianceFindings() {
        Instant now = Instant.now();
        // A recent DRAWING only — SPECIFICATION and CONTRACT_DOCUMENT are missing → compliance gap.
        List<DocumentResponse> docs = List.of(doc(DocumentType.DRAWING, now.minus(Duration.ofDays(1))));
        when(documentService.listDocuments(PROJECT)).thenReturn(docs);
        // A live permit ~2.5 days out → truncates to 2 days remaining → HIGH severity band (>1, <=3).
        Permit expiring = permit(PermitStatus.ISSUED, now.plus(Duration.ofHours(60)));
        when(permitRepository.findByProjectIdRecent(eq(PROJECT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expiring)));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        List<AgentFindingDraft> c = result.candidates();
        assertThat(c).extracting(AgentFindingDraft::findingType)
                .containsExactlyInAnyOrder("PERMIT_EXPIRY", "DOCUMENT_SUMMARY", "COMPLIANCE_DOC_GAP");

        // Most-severe first: permit expiry in 2 days → HIGH.
        assertThat(c.get(0).findingType()).isEqualTo("PERMIT_EXPIRY");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(c.get(0).subjectRef()).isEqualTo("PROJECT");
        assertThat(c.get(0).evidence()).anySatisfy(e -> assertThat(e.label()).isEqualTo("Permits expiring"));

        AgentFindingDraft gap = c.stream().filter(f -> f.findingType().equals("COMPLIANCE_DOC_GAP"))
                .findFirst().orElseThrow();
        assertThat(gap.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(gap.whatHappened()).contains("SPECIFICATION").contains("CONTRACT_DOCUMENT");

        AgentFindingDraft summary = c.stream().filter(f -> f.findingType().equals("DOCUMENT_SUMMARY"))
                .findFirst().orElseThrow();
        assertThat(summary.severity()).isEqualTo(Severity.INFO);
        assertThat(summary.confidence()).isBetween(0.0, 1.0);

        assertThat(result.dataSnapshot().get("expiringPermits").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("recentDocuments").asInt()).isEqualTo(1);
        assertThat(result.dataSnapshot().get("totalDocuments").asInt()).isEqualTo(1);
    }

    @Test
    void expiredAndNonLivePermitsAreIgnored() {
        Instant now = Instant.now();
        when(documentService.listDocuments(PROJECT)).thenReturn(List.of());
        // Already expired (endAt in the past) and a DRAFT well within the window — neither should flag.
        Permit past = permit(PermitStatus.ISSUED, now.minus(Duration.ofDays(1)));
        Permit draft = permit(PermitStatus.DRAFT, now.plus(Duration.ofDays(2)));
        when(permitRepository.findByProjectIdRecent(eq(PROJECT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(past, draft)));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("expiringPermits").asInt()).isZero();
    }

    @Test
    void noDataYieldsNoFindings() {
        when(documentService.listDocuments(PROJECT)).thenReturn(List.of());
        when(permitRepository.findByProjectIdRecent(eq(PROJECT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.dataSnapshot().get("totalDocuments").asInt()).isZero();
    }
}
