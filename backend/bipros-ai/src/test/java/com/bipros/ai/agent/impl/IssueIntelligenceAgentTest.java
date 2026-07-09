package com.bipros.ai.agent.impl;

import com.bipros.ai.agent.core.AgentFindingDraft;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.core.GatherResult;
import com.bipros.ai.agent.core.Severity;
import com.bipros.project.application.dto.DprIssueRow;
import com.bipros.project.application.service.DprIssueService;
import com.bipros.project.domain.model.HseIncidentType;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import com.bipros.siteops.application.dto.NcrResponse;
import com.bipros.siteops.application.dto.SafetyRecordResponse;
import com.bipros.siteops.application.dto.SnagResponse;
import com.bipros.siteops.application.service.NcrService;
import com.bipros.siteops.application.service.SafetyService;
import com.bipros.siteops.application.service.SnagService;
import com.bipros.siteops.domain.model.NcrCategory;
import com.bipros.siteops.domain.model.NcrSeverity;
import com.bipros.siteops.domain.model.NcrSourceType;
import com.bipros.siteops.domain.model.NcrStatus;
import com.bipros.siteops.domain.model.SafetyKind;
import com.bipros.siteops.domain.model.SafetySeverity;
import com.bipros.siteops.domain.model.SnagSeverity;
import com.bipros.siteops.domain.model.SnagStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueIntelligenceAgentTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant NOW = Instant.now();

    @Mock
    private DprIssueService dprIssueService;
    @Mock
    private NcrService ncrService;
    @Mock
    private SnagService snagService;
    @Mock
    private SafetyService safetyService;

    private IssueIntelligenceAgent agent() {
        return new IssueIntelligenceAgent(
                dprIssueService, ncrService, snagService, safetyService, new ObjectMapper());
    }

    private static AgentFindingDraft byType(List<AgentFindingDraft> c, String type) {
        return c.stream().filter(f -> f.findingType().equals(type)).findFirst().orElseThrow();
    }

    private static AgentFindingDraft bySubject(List<AgentFindingDraft> c, String type, String subject) {
        return c.stream()
                .filter(f -> f.findingType().equals(type) && f.subjectRef().equals(subject))
                .findFirst().orElseThrow();
    }

    // ----------------------------------------------------------------- fixtures

    private DprIssueRow dprRow(UUID id, IssueCategory cat, IssueStatus status,
                               HseIncidentType hse, Instant openedAt) {
        return new DprIssueRow(
                id, UUID.randomUUID(), null, null, LocalDate.now(),
                "Issue " + id.toString().substring(0, 4), "desc",
                cat, IssueSeverity.HIGH, status,
                null, null, null, null,
                openedAt, null, null, null, null, hse);
    }

    private NcrResponse ncr(UUID id, NcrCategory cat, NcrStatus status, Instant raisedAt) {
        return new NcrResponse(
                id, PROJECT, "NCR-0001", "NCR title", "desc",
                cat, NcrSeverity.MEDIUM, status,
                null, raisedAt, null, null, null, null, null,
                raisedAt, raisedAt, NcrSourceType.MANUAL, null, null);
    }

    private SnagResponse snag(UUID id, SnagStatus status, Instant raisedAt) {
        return new SnagResponse(
                id, PROJECT, null, "L1", "A snag", SnagSeverity.MEDIUM, status,
                UUID.randomUUID(), raisedAt, null, null, null,
                raisedAt, null, raisedAt, null);
    }

    private SafetyRecordResponse safety(UUID id, SafetyKind kind, Instant occurredAt) {
        return new SafetyRecordResponse(
                id, PROJECT, kind, occurredAt, "L1", SafetySeverity.HIGH,
                "A safety event", "acted", null, null, occurredAt, occurredAt);
    }

    private void stub(List<DprIssueRow> dpr, List<NcrResponse> ncrs,
                      List<SnagResponse> snags, List<SafetyRecordResponse> safety) {
        when(dprIssueService.list(PROJECT, null, null, null, null, null, null, null, null)).thenReturn(dpr);
        when(ncrService.list(PROJECT, null)).thenReturn(ncrs);
        when(snagService.listByProject(PROJECT, null)).thenReturn(snags);
        when(safetyService.list(PROJECT, null)).thenReturn(safety);
    }

    // ----------------------------------------------------------------- tests

    @Test
    void emitsHseAgeingAndRecurringFindings() {
        List<DprIssueRow> dpr = new ArrayList<>();
        // Open HSE issue (LTI, category SAFETY) opened 3 days ago -> HSE_OPEN_CRITICAL (CRITICAL).
        dpr.add(dprRow(UUID.randomUUID(), IssueCategory.SAFETY, IssueStatus.OPEN,
                HseIncidentType.LTI, NOW.minus(Duration.ofDays(3))));
        // Three open QUALITY DPR issues -> recurring cluster "DPR/QUALITY" (3).
        for (int i = 0; i < 3; i++) {
            dpr.add(dprRow(UUID.randomUUID(), IssueCategory.QUALITY, IssueStatus.IN_PROGRESS,
                    null, NOW.minus(Duration.ofDays(1))));
        }

        List<NcrResponse> ncrs = new ArrayList<>();
        // Four open QUALITY NCRs raised 10 days ago -> ISSUE_AGEING (ncr, HIGH).
        for (int i = 0; i < 4; i++) {
            ncrs.add(ncr(UUID.randomUUID(), NcrCategory.QUALITY, NcrStatus.OPEN, NOW.minus(Duration.ofDays(10))));
        }
        // One open SAFETY NCR raised 5 days ago -> feeds HSE_OPEN_CRITICAL, not the NCR ageing bucket.
        ncrs.add(ncr(UUID.randomUUID(), NcrCategory.SAFETY, NcrStatus.IN_REVIEW, NOW.minus(Duration.ofDays(5))));
        // A CLOSED NCR that must be ignored.
        ncrs.add(ncr(UUID.randomUUID(), NcrCategory.QUALITY, NcrStatus.CLOSED, NOW.minus(Duration.ofDays(30))));

        List<SnagResponse> snags = new ArrayList<>();
        // Three open snags raised 20 days ago -> ISSUE_AGEING (snag, MEDIUM).
        for (int i = 0; i < 3; i++) {
            snags.add(snag(UUID.randomUUID(), SnagStatus.OPEN, NOW.minus(Duration.ofDays(20))));
        }

        List<SafetyRecordResponse> safety = new ArrayList<>();
        // An open INCIDENT -> HSE; an OBSERVATION must be ignored (not an open concern).
        safety.add(safety(UUID.randomUUID(), SafetyKind.INCIDENT, NOW.minus(Duration.ofDays(2))));
        safety.add(safety(UUID.randomUUID(), SafetyKind.OBSERVATION, NOW.minus(Duration.ofDays(2))));

        stub(dpr, ncrs, snags, safety);

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).extracting(AgentFindingDraft::findingType)
                .contains("HSE_OPEN_CRITICAL", "ISSUE_AGEING", "RECURRING_ISSUE_PATTERN");

        AgentFindingDraft hse = byType(c, "HSE_OPEN_CRITICAL");
        assertThat(hse.severity()).isEqualTo(Severity.CRITICAL);   // oldest open HSE > 24h
        assertThat(hse.subjectRef()).isEqualTo("PROJECT");
        assertThat(hse.evidence()).anySatisfy(e ->
                assertThat(e.label()).isEqualTo("Open HSE/safety issues"));

        assertThat(bySubject(c, "ISSUE_AGEING", "ncr").severity()).isEqualTo(Severity.HIGH);
        assertThat(bySubject(c, "ISSUE_AGEING", "snag").severity()).isEqualTo(Severity.MEDIUM);

        AgentFindingDraft pattern = byType(c, "RECURRING_ISSUE_PATTERN");
        assertThat(pattern.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(pattern.confidence()).isLessThanOrEqualTo(0.6);   // capped

        // Most-severe first.
        assertThat(c.get(0).severity()).isEqualTo(Severity.CRITICAL);

        // Snapshot carries the deterministic counts.
        assertThat(result.dataSnapshot().get("hseOpen").asInt()).isEqualTo(3);   // 1 DPR + 1 NCR + 1 safety
        assertThat(result.dataSnapshot().get("agedNcr").asInt()).isEqualTo(4);
        assertThat(result.dataSnapshot().get("agedSnag").asInt()).isEqualTo(3);
    }

    @Test
    void freshHseIssueIsHighNotCritical() {
        List<DprIssueRow> dpr = List.of(dprRow(UUID.randomUUID(), IssueCategory.SAFETY,
                IssueStatus.OPEN, HseIncidentType.MTC, NOW.minus(Duration.ofHours(3))));
        stub(dpr, List.of(), List.of(), List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));
        List<AgentFindingDraft> c = result.candidates();

        assertThat(c).hasSize(1);
        assertThat(c.get(0).findingType()).isEqualTo("HSE_OPEN_CRITICAL");
        assertThat(c.get(0).severity()).isEqualTo(Severity.HIGH);   // open < 24h
    }

    @Test
    void noDataYieldsNoFindings() {
        stub(List.of(), List.of(), List.of(), List.of());

        GatherResult result = agent().gather(AgentRunContext.manual(PROJECT, null));

        assertThat(result.candidates()).isEmpty();
    }
}
