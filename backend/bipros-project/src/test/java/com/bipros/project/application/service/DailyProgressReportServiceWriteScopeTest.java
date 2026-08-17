package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.security.DataScope;
import com.bipros.common.security.ScopeKeys;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyProgressReportRequest;
import com.bipros.project.application.dto.UpdateDailyProgressReportRequest;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprAttachmentRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprIssueRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.project.domain.repository.DprVoiceNoteRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.infrastructure.storage.DprAttachmentStorageService;
import com.bipros.project.infrastructure.storage.VoiceNoteStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gate-3 WRITE-path enforcement (the read path is covered by {@code DailyProgressReportScopeTest}):
 * a person-scoped caller may only file DPRs against activities their member set supervises, and
 * only under a supervisor within that set. The scoped activity picker is UX only — these tests
 * prove the server rejects a crafted payload that bypasses it. "Correct-looking but wrong"
 * coverage: an accepted foreign DPR would silently poison another supervisor's actuals.
 *
 * <p>Same harness shape as {@code DailyProgressReportServiceDraftRejectionTest}: native SQL
 * stubbed through one mock {@link Query}; the scope resolver is a lambda returning OWN keys.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DailyProgressReportService — gate-3 write scoping")
class DailyProgressReportServiceWriteScopeTest {

  @Mock private DailyProgressReportRepository dprRepository;
  @Mock private DprManpowerRepository manpowerRepository;
  @Mock private DprEquipmentRepository equipmentRepository;
  @Mock private DprMaterialRepository materialRepository;
  @Mock private com.bipros.project.domain.repository.DprSubContractorRepository subContractorRepository;
  @Mock private DprAttachmentRepository attachmentRepository;
  @Mock private DprVoiceNoteRepository voiceNoteRepository;
  @Mock private DprIssueRepository issueRepository;
  @Mock private DprAttachmentStorageService attachmentStorage;
  @Mock private VoiceNoteStorage voiceNoteStorage;
  @Mock private ProjectRepository projectRepository;
  @Mock private DailyActivityResourceOutputService ledgerService;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private com.bipros.project.domain.repository.BoqItemRepository boqItemRepository;

  @Mock private EntityManager entityManager;
  @Mock private Query query;

  private DailyProgressReportService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID dprId = UUID.randomUUID();
  private final UUID me = UUID.randomUUID();               // the OWN-scoped caller
  private final UUID myActivity = UUID.randomUUID();       // activity I supervise
  private final UUID foreignActivity = UUID.randomUUID();  // someone else's activity
  private final UUID otherUser = UUID.randomUUID();        // not in my member set

  @BeforeEach
  void setUp() {
    // OWN scope: member set is the caller alone (ScopeKeys compat constructor).
    service = new DailyProgressReportService(
        dprRepository, manpowerRepository, equipmentRepository, materialRepository,
        subContractorRepository, attachmentRepository, voiceNoteRepository, issueRepository,
        attachmentStorage, voiceNoteStorage, projectRepository, ledgerService, auditService,
        eventPublisher, null, boqItemRepository, null, null, null, null,
        () -> new ScopeKeys(DataScope.OWN, me, Set.of("Some Supervisor")));

    ReflectionTestUtils.setField(service, "em", entityManager);

    // EntityManager chain: createNativeQuery(...).setParameter(...).getResultList() / executeUpdate().
    // Per-test the getResultList() sequence matters: 1st call feeds rejectIfActivityDraft, 2nd
    // feeds teamActivityIds (the new write-scope gate); later calls default to empty lists.
    lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    lenient().when(query.executeUpdate()).thenReturn(0);

    lenient().when(projectRepository.existsById(projectId)).thenReturn(true);
    lenient().when(dprRepository.save(any())).thenAnswer(inv -> {
      DailyProgressReport d = inv.getArgument(0);
      if (d.getId() == null) d.setId(dprId);
      return d;
    });
    lenient().when(dprRepository.sumQtyExecutedThroughDate(any(), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    lenient().when(dprRepository.findFirstByProjectIdAndReportDateAndActivityIdAndSupervisorUserId(
            any(), any(), any(), any())).thenReturn(Optional.empty());
    lenient().when(manpowerRepository.saveAll(any())).thenReturn(List.of());
    lenient().when(equipmentRepository.saveAll(any())).thenReturn(List.of());
    lenient().when(materialRepository.saveAll(any())).thenReturn(List.of());
    lenient().when(attachmentRepository.findByDprIdOrderByCreatedAtAsc(any()))
        .thenReturn(List.of());
    lenient().when(issueRepository.findByDprIdOrderByOpenedAtAsc(any())).thenReturn(List.of());
    lenient().when(issueRepository.saveAll(any())).thenReturn(List.of());
  }

  @Test
  @DisplayName("OWN create against a foreign activity answers ACTIVITY_NOT_FOUND (no leak) and persists nothing")
  void createRejectsForeignActivity() {
    // 1st getResultList → activity status (LOCKED, so the draft gate passes);
    // 2nd getResultList → teamActivityIds: I supervise myActivity, NOT foreignActivity.
    when(query.getResultList())
        .thenReturn(java.util.Collections.singletonList(new Object[]{"LOCKED", "TEST-001"}))
        .thenReturn(List.<Object>of(myActivity));

    assertThatThrownBy(() -> service.create(projectId, createRequest(foreignActivity, me)))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(t -> assertThat(((BusinessRuleException) t).getRuleCode())
            .isEqualTo("ACTIVITY_NOT_FOUND"));
    verify(dprRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("OWN create against my own activity passes the scope gate (DPR persists)")
  void createPassesOwnActivity() {
    // 1st → activity status; 2nd → teamActivityIds contains the requested activity;
    // remaining calls (BOQ link, ensureAssignmentsExist, …) → empty.
    when(query.getResultList())
        .thenReturn(java.util.Collections.singletonList(new Object[]{"LOCKED", "TEST-002"}))
        .thenReturn(List.<Object>of(myActivity))
        .thenReturn(List.of());

    assertThatCode(() -> service.create(projectId, createRequest(myActivity, me)))
        .doesNotThrowAnyException();

    verify(dprRepository).save(any(DailyProgressReport.class));
  }

  @Test
  @DisplayName("OWN create under someone else's name rejects DPR_SUPERVISOR_OUT_OF_SCOPE")
  void createRejectsSupervisorSpoof() {
    // Activity is mine — only the supervisor id is foreign.
    when(query.getResultList())
        .thenReturn(java.util.Collections.singletonList(new Object[]{"LOCKED", "TEST-003"}))
        .thenReturn(List.<Object>of(myActivity));

    assertThatThrownBy(() -> service.create(projectId, createRequest(myActivity, otherUser)))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(t -> assertThat(((BusinessRuleException) t).getRuleCode())
            .isEqualTo("DPR_SUPERVISOR_OUT_OF_SCOPE"));
    verify(dprRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("OWN update re-pointing the DPR at a foreign activity answers ACTIVITY_NOT_FOUND")
  void updateRejectsRepointToForeignActivity() {
    // The existing row is mine (supervisorUserId = me), so find() lets it through without
    // touching teamActivityIds; the changed-activity gate then rejects the new link.
    DailyProgressReport existing = baseDpr();
    when(dprRepository.findById(dprId)).thenReturn(Optional.of(existing));
    when(query.getResultList())
        .thenReturn(java.util.Collections.singletonList(new Object[]{"LOCKED", "TEST-004"}))
        .thenReturn(List.<Object>of(myActivity));

    assertThatThrownBy(() -> service.update(projectId, dprId, updateRequest(foreignActivity, me)))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(t -> assertThat(((BusinessRuleException) t).getRuleCode())
            .isEqualTo("ACTIVITY_NOT_FOUND"));
    verify(dprRepository, org.mockito.Mockito.never()).save(any());
  }

  // ─── helpers ────────────────────────────────────────────────────────────────

  private CreateDailyProgressReportRequest createRequest(UUID activityId, UUID supervisorUserId) {
    return new CreateDailyProgressReportRequest(
        LocalDate.of(2026, 5, 1), supervisorUserId, "Some Supervisor",
        null, null, activityId, "Test Activity", null, null, null, "Cum",
        new BigDecimal("10.0"), null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null);
  }

  private UpdateDailyProgressReportRequest updateRequest(UUID activityId, UUID supervisorUserId) {
    return new UpdateDailyProgressReportRequest(
        LocalDate.of(2026, 5, 1), supervisorUserId, "Some Supervisor",
        null, null, activityId, "Test Activity", null, null, null, "Cum",
        new BigDecimal("10.0"), null, null,
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null);
  }

  private DailyProgressReport baseDpr() {
    DailyProgressReport d = DailyProgressReport.builder()
        .projectId(projectId)
        .reportDate(LocalDate.of(2026, 5, 1))
        .supervisorUserId(me)
        .supervisorName("Some Supervisor")
        .activityId(myActivity)
        .activityName("Test Activity")
        .unit("Cum")
        .qtyExecuted(new BigDecimal("10.0"))
        .build();
    d.setId(dprId);
    return d;
  }
}
