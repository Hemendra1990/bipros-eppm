package com.bipros.api.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.api.dto.UnitConsistencyRepairRequest;
import com.bipros.api.dto.UnitConsistencyRepairResponse;
import com.bipros.api.dto.UnitConsistencyRepairResponse.BoqConflict;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Sample;
import com.bipros.api.dto.UnitConsistencyRepairResponse.UnmappedActivity;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UnitConsistencyRepairService#repair}, the admin endpoint that anchors
 * every unit in the WorkActivity → ProductivityNorm → Activity → DPR → BOQ chain to a single
 * canonical spelling per measure (per {@code UnitNormalizer}).
 */
@ExtendWith(MockitoExtension.class)
class UnitConsistencyRepairServiceTest {

  @Mock ActivityRepository activityRepo;
  @Mock WorkActivityRepository workActivityRepo;
  @Mock ProductivityNormRepository productivityNormRepo;
  @Mock DailyProgressReportRepository dprRepo;
  @Mock BoqItemRepository boqItemRepo;
  @Mock AuditService auditService;

  UnitConsistencyRepairService service;

  @BeforeEach
  void setUp() {
    // Build directly via the constructor (mirrors ProjectDataRepairServiceActivityStatusTest);
    // wire the self-proxy field manually since there's no Spring context in a pure Mockito test.
    service = new UnitConsistencyRepairService(
        activityRepo, workActivityRepo, productivityNormRepo, dprRepo, boqItemRepo, auditService);
    service.self = service;
  }

  private Activity activity(UUID id, UUID projectId, UUID workActivityId, String code, String name) {
    Activity a = new Activity();
    a.setId(id);
    a.setProjectId(projectId);
    a.setWorkActivityId(workActivityId);
    a.setCode(code);
    a.setName(name);
    return a;
  }

  private WorkActivity workActivity(UUID id, String defaultUnit) {
    WorkActivity wa = new WorkActivity();
    wa.setId(id);
    wa.setDefaultUnit(defaultUnit);
    return wa;
  }

  private BoqItem boqItem(UUID id, UUID projectId, String itemNo, String unit) {
    BoqItem item = new BoqItem();
    item.setId(id);
    item.setProjectId(projectId);
    item.setItemNo(itemNo);
    item.setUnit(unit);
    return item;
  }

  private UnitConsistencyRepairRequest request(boolean dryRun, List<String> phases) {
    UnitConsistencyRepairRequest req = new UnitConsistencyRepairRequest();
    req.setDryRun(dryRun);
    if (phases != null) {
      req.setPhases(phases);
    }
    return req;
  }

  // ---- 1. DPR relabel: synonym + exact-canonical rows grouped and bulk-relabeled ----

  @Test
  void dprRowsGroupedByCanonicalUnit_synonymAndAlreadyConsistentRows() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();
    UUID dprLtrId = UUID.randomUUID();
    UUID dprCumId = UUID.randomUUID();
    UUID dprSynonymId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-1", "Earthwork");
    WorkActivity wa = workActivity(waId, "Cum");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.of(
        new Object[]{dprLtrId, activityId, "Ltr"},
        new Object[]{dprCumId, activityId, "Cum"},
        new Object[]{dprSynonymId, activityId, "cu.m."}
    ));
    when(dprRepo.bulkSetUnit(anyList(), eq("Cum"))).thenReturn(2);

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("DPR")));

    ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(dprRepo).bulkSetUnit(idsCaptor.capture(), eq("Cum"));
    assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(dprLtrId, dprSynonymId);

    assertThat(resp.summary().dpr().alreadyConsistent()).isEqualTo(1);
    assertThat(resp.summary().dpr().relabeled()).isEqualTo(2);
    assertThat(resp.summary().dpr().scanned()).isEqualTo(3);
  }

  // ---- 2. Unmapped activity: skipped + reported once with correct dprCount ----

  @Test
  void unmappedActivity_skipsDprsAndReportsOnceWithCount() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    // No workActivityId -> unmapped.
    Activity act = activity(activityId, projectId, null, "A-2", "Fencing");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.of(
        new Object[]{UUID.randomUUID(), activityId, "Nos"},
        new Object[]{UUID.randomUUID(), activityId, "Ltr"}
    ));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("DPR")));

    assertThat(resp.summary().dpr().skippedUnmapped()).isEqualTo(2);
    assertThat(resp.unmappedActivities()).hasSize(1);
    UnmappedActivity ua = resp.unmappedActivities().get(0);
    assertThat(ua.activityId()).isEqualTo(activityId);
    assertThat(ua.code()).isEqualTo("A-2");
    assertThat(ua.name()).isEqualTo("Fencing");
    assertThat(ua.dprCount()).isEqualTo(2);

    verify(dprRepo, never()).bulkSetUnit(anyList(), anyString());
  }

  // ---- 3. DPR row with no activityId ----

  @Test
  void dprWithNullActivityId_skippedNoActivity() {
    UUID projectId = UUID.randomUUID();

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of());
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{UUID.randomUUID(), null, "Ltr"}
    ));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("DPR")));

    assertThat(resp.summary().dpr().skippedNoActivity()).isEqualTo(1);
    assertThat(resp.summary().dpr().scanned()).isEqualTo(1);
    verify(dprRepo, never()).bulkSetUnit(anyList(), anyString());
  }

  // ---- 4. dryRun performs zero writes across all phases, but still reports intended counts ----

  @Test
  void dryRun_neverWritesAnywhere_butReportsIntendedCounts() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();
    UUID dprId = UUID.randomUUID();
    UUID boqItemId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-4", "Piling");
    WorkActivity wa = workActivity(waId, "cu.m."); // needs ANCHOR normalization -> "Cum"
    ProductivityNorm norm = ProductivityNorm.builder().workActivity(wa).unit("cum").build();
    norm.setId(UUID.randomUUID());

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(productivityNormRepo.findByWorkActivityIdIn(List.of(waId))).thenReturn(List.of(norm));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{dprId, activityId, "Ltr"}
    ));
    when(dprRepo.findDistinctBoqItemActivityPairsByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{boqItemId, activityId}
    ));
    when(boqItemRepo.findByProjectId(projectId)).thenReturn(List.of(
        boqItem(boqItemId, projectId, "B-1", "Ltr")
    ));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(true, null));

    verify(workActivityRepo, never()).save(any());
    verify(productivityNormRepo, never()).save(any());
    verify(dprRepo, never()).bulkSetUnit(anyList(), anyString());
    verify(boqItemRepo, never()).bulkSetUnit(anyList(), anyString());
    verifyNoInteractions(auditService);

    assertThat(resp.summary().anchor().workActivitiesNormalized()).isEqualTo(1);
    assertThat(resp.summary().anchor().normsNormalized()).isEqualTo(1);
    assertThat(resp.summary().dpr().relabeled()).isEqualTo(1);
    assertThat(resp.summary().boq().relabeled()).isEqualTo(1);
    assertThat(resp.samples()).isNotEmpty();
  }

  // ---- 5. BOQ: single-unit relabel + multi-unit conflict ----

  @Test
  void boqSingleUnitRelabeled_multiUnitConflictSkippedAndReported() {
    UUID projectId = UUID.randomUUID();
    UUID activity1 = UUID.randomUUID();
    UUID activity2 = UUID.randomUUID();
    UUID wa1 = UUID.randomUUID();
    UUID wa2 = UUID.randomUUID();
    UUID boqSingle = UUID.randomUUID();
    UUID boqConflict = UUID.randomUUID();

    Activity act1 = activity(activity1, projectId, wa1, "A-5a", "Excavation");
    Activity act2 = activity(activity2, projectId, wa2, "A-5b", "Pipe Laying");
    WorkActivity workActivity1 = workActivity(wa1, "Cum");
    WorkActivity workActivity2 = workActivity(wa2, "m");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act1, act2));
    when(workActivityRepo.findAllById(List.of(wa1, wa2))).thenReturn(List.of(workActivity1, workActivity2));
    when(dprRepo.findDistinctBoqItemActivityPairsByProjectId(projectId)).thenReturn(List.of(
        new Object[]{boqSingle, activity1},
        new Object[]{boqConflict, activity1},
        new Object[]{boqConflict, activity2}
    ));
    when(boqItemRepo.findByProjectId(projectId)).thenReturn(List.of(
        boqItem(boqSingle, projectId, "B-SINGLE", "Ltr"),
        boqItem(boqConflict, projectId, "B-CONFLICT", "Nos")
    ));
    when(boqItemRepo.bulkSetUnit(eq(List.of(boqSingle)), eq("Cum"))).thenReturn(1);

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("BOQ")));

    verify(boqItemRepo, times(1)).bulkSetUnit(eq(List.of(boqSingle)), eq("Cum"));
    verify(boqItemRepo, times(1)).bulkSetUnit(anyList(), anyString());

    assertThat(resp.summary().boq().relabeled()).isEqualTo(1);
    assertThat(resp.summary().boq().skippedConflict()).isEqualTo(1);
    assertThat(resp.boqConflicts()).hasSize(1);
    BoqConflict conflict = resp.boqConflicts().get(0);
    assertThat(conflict.boqItemId()).isEqualTo(boqConflict);
    assertThat(conflict.itemNo()).isEqualTo("B-CONFLICT");
    assertThat(conflict.currentUnit()).isEqualTo("Nos");
    assertThat(conflict.candidateUnits()).containsExactly("Cum", "m");
  }

  // ---- 6. BOQ referenced only by an unmapped activity ----

  @Test
  void boqReferencedOnlyByUnmappedActivity_skippedUnused() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID boqItemId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, null, "A-6", "Unmapped");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(dprRepo.findDistinctBoqItemActivityPairsByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{boqItemId, activityId}
    ));
    when(boqItemRepo.findByProjectId(projectId)).thenReturn(List.of(
        boqItem(boqItemId, projectId, "B-6", "Nos")
    ));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("BOQ")));

    assertThat(resp.summary().boq().skippedUnused()).isEqualTo(1);
    assertThat(resp.summary().boq().scanned()).isEqualTo(1);
    verify(boqItemRepo, never()).bulkSetUnit(anyList(), anyString());
  }

  // ---- 6b. BOQ item with zero DPR references at all (not even via an unmapped activity):
  // still included in scanned and reported skippedUnused, per the full-BOQ-population fix ----

  @Test
  void boqItemWithZeroDprReferences_scannedAndSkippedUnused() {
    UUID projectId = UUID.randomUUID();
    UUID boqItemId = UUID.randomUUID();

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of());
    when(dprRepo.findDistinctBoqItemActivityPairsByProjectId(projectId)).thenReturn(List.of());
    when(boqItemRepo.findByProjectId(projectId)).thenReturn(List.of(
        boqItem(boqItemId, projectId, "B-ZERO", "Nos")
    ));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("BOQ")));

    assertThat(resp.summary().boq().scanned()).isEqualTo(1);
    assertThat(resp.summary().boq().skippedUnused()).isEqualTo(1);
    verify(boqItemRepo, never()).bulkSetUnit(anyList(), anyString());
  }

  // ---- 7. ANCHOR: WorkActivity + ProductivityNorm normalized to canonical spelling ----

  @Test
  void anchorNormalizesWorkActivityAndNorm() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-7", "Concreting");
    WorkActivity wa = workActivity(waId, "cu.m.");
    ProductivityNorm norm = ProductivityNorm.builder().workActivity(wa).unit("cum").build();
    norm.setId(UUID.randomUUID());

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(productivityNormRepo.findByWorkActivityIdIn(List.of(waId))).thenReturn(List.of(norm));

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("ANCHOR")));

    ArgumentCaptor<WorkActivity> waCaptor = ArgumentCaptor.forClass(WorkActivity.class);
    verify(workActivityRepo).save(waCaptor.capture());
    assertThat(waCaptor.getValue().getDefaultUnit()).isEqualTo("Cum");

    ArgumentCaptor<ProductivityNorm> normCaptor = ArgumentCaptor.forClass(ProductivityNorm.class);
    verify(productivityNormRepo).save(normCaptor.capture());
    assertThat(normCaptor.getValue().getUnit()).isEqualTo("Cum");

    assertThat(resp.summary().anchor().workActivitiesNormalized()).isEqualTo(1);
    assertThat(resp.summary().anchor().normsNormalized()).isEqualTo(1);

    verifyNoInteractions(dprRepo);
    verifyNoInteractions(boqItemRepo);
  }

  // ---- 8. chunkSize splits a larger group into multiple bulk calls covering all ids ----

  @Test
  void chunking_splitsGroupIntoMultipleBulkCallsCoveringAllIds() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-8", "Backfilling");
    WorkActivity wa = workActivity(waId, "Cum");

    List<UUID> dprIds = List.of(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID());
    List<Object[]> rows = dprIds.stream()
        .map(id -> new Object[]{id, activityId, "Ltr"})
        .toList();

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(rows);
    when(dprRepo.bulkSetUnit(anyList(), eq("Cum")))
        .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

    UnitConsistencyRepairRequest req = request(false, List.of("DPR"));
    req.setChunkSize(2);

    UnitConsistencyRepairResponse resp = service.repair(projectId, req);

    ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(dprRepo, times(3)).bulkSetUnit(idsCaptor.capture(), eq("Cum"));

    List<List<UUID>> chunks = idsCaptor.getAllValues();
    assertThat(chunks).extracting(List::size).containsExactly(2, 2, 1);
    assertThat(chunks.stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(dprIds);

    assertThat(resp.summary().dpr().relabeled()).isEqualTo(5);
  }

  // ---- 9. phases filter: only the requested phase runs ----

  @Test
  void phasesFilter_onlyDprRuns_anchorAndBoqUntouched() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();
    UUID dprId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-9", "Grading");
    // WA unit is a synonym that WOULD be normalized if ANCHOR ran -- it must not run.
    WorkActivity wa = workActivity(waId, "cu.m.");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{dprId, activityId, "Ltr"}
    ));
    when(dprRepo.bulkSetUnit(anyList(), eq("Cum"))).thenReturn(1);

    UnitConsistencyRepairResponse resp =
        service.repair(projectId, request(false, List.of("DPR")));

    assertThat(resp.summary().anchor().workActivitiesNormalized()).isZero();
    assertThat(resp.summary().anchor().normsNormalized()).isZero();
    assertThat(resp.summary().boq().scanned()).isZero();
    assertThat(resp.summary().boq().relabeled()).isZero();

    verify(workActivityRepo, never()).save(any());
    verify(productivityNormRepo, never()).findByWorkActivityIdIn(anyList());
    verify(productivityNormRepo, never()).save(any());
    verifyNoInteractions(boqItemRepo);
    verify(dprRepo, never()).findDistinctBoqItemActivityPairsByProjectId(any());

    assertThat(resp.summary().dpr().relabeled()).isEqualTo(1);
  }

  // ---- 10. non-dryRun relabel fires the audit log (apply-side counterpart to test 4's
  // dryRun-never-audits assertion) ----

  @Test
  void nonDryRunRelabel_firesAuditLog() {
    UUID projectId = UUID.randomUUID();
    UUID activityId = UUID.randomUUID();
    UUID waId = UUID.randomUUID();
    UUID dprId = UUID.randomUUID();

    Activity act = activity(activityId, projectId, waId, "A-10", "Curing");
    WorkActivity wa = workActivity(waId, "Cum");

    when(activityRepo.findByProjectId(projectId)).thenReturn(List.of(act));
    when(workActivityRepo.findAllById(List.of(waId))).thenReturn(List.of(wa));
    when(dprRepo.findIdActivityUnitByProjectId(projectId)).thenReturn(List.<Object[]>of(
        new Object[]{dprId, activityId, "Ltr"}
    ));
    when(dprRepo.bulkSetUnit(anyList(), eq("Cum"))).thenReturn(1);

    service.repair(projectId, request(false, List.of("DPR")));

    verify(auditService, atLeastOnce()).logUpdate(
        eq("Project"), eq(projectId), eq("unit-repair"), isNull(), eq("DPR:relabeled=1"));
  }
}
