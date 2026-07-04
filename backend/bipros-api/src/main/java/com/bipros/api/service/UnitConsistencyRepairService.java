package com.bipros.api.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.api.dto.UnitConsistencyRepairRequest;
import com.bipros.api.dto.UnitConsistencyRepairResponse;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Anchor;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Boq;
import com.bipros.api.dto.UnitConsistencyRepairResponse.BoqConflict;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Dpr;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Sample;
import com.bipros.api.dto.UnitConsistencyRepairResponse.Summary;
import com.bipros.api.dto.UnitConsistencyRepairResponse.UnmappedActivity;
import com.bipros.common.unit.UnitNormalizer;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin data-repair: anchors every unit in the
 * {@code WorkActivity.defaultUnit → ProductivityNorm.unit → Activity → DPR.unit → BoqItem.unit}
 * chain to one canonical spelling per measure (see {@link UnitNormalizer}). Pure relabel — never
 * changes a quantity, cost, or EVM value; see the design spec
 * ({@code docs/superpowers/specs/2026-07-04-unit-consistency-repair-endpoint-design.md}) for the
 * full rationale and phased algorithm (§5).
 *
 * <p>Runs in three independent, id-chunked phases (ANCHOR, DPR, BOQ), each committing its own
 * chunk-sized transactions via the {@link #self} proxy so a 9k-row project doesn't hold one giant
 * transaction. {@code dryRun} (default true) computes and reports every count/sample with ZERO
 * writes — no repository save/bulk-update call and no audit log is ever made in that mode.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitConsistencyRepairService {

  private static final int MAX_SAMPLES = 50;
  private static final int DEFAULT_CHUNK_SIZE = 500;
  // Upper bound so a caller-supplied chunkSize can't collapse the per-chunk-transaction safety
  // into one huge transaction / oversized IN (:ids) bind list on a ~9k-row project.
  private static final int MAX_CHUNK_SIZE = 2000;

  private final ActivityRepository activityRepo;
  private final WorkActivityRepository workActivityRepo;
  private final ProductivityNormRepository productivityNormRepo;
  private final DailyProgressReportRepository dprRepo;
  private final BoqItemRepository boqItemRepo;
  private final AuditService auditService;

  // Self-proxy: lets repair(...) route each chunk write through the Spring proxy so it runs in
  // (and commits) its own transaction. NOT in the Lombok constructor — non-final, package-private
  // so the pure-Mockito unit test can wire it (service.self = service) without a Spring context.
  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  UnitConsistencyRepairService self;

  /**
   * Runs the requested phases (default all three: ANCHOR, DPR, BOQ) and returns a bounded report
   * (counts in {@code summary}, capped {@code samples}, full lists of items needing manual
   * attention). Deliberately NOT {@code @Transactional} — each chunk write commits independently
   * via {@link #self} so a mid-run failure leaves prior chunks committed and a re-run is safe
   * (idempotent: relabel only touches rows that differ from the canonical spelling).
   */
  public UnitConsistencyRepairResponse repair(UUID projectId, UnitConsistencyRepairRequest req) {
    boolean dry = req.isDryRun();
    int chunkSize = Math.min(req.getChunkSize() > 0 ? req.getChunkSize() : DEFAULT_CHUNK_SIZE, MAX_CHUNK_SIZE);
    Set<String> phases = (req.getPhases() == null || req.getPhases().isEmpty())
        ? new HashSet<>(List.of("ANCHOR", "DPR", "BOQ"))
        : new HashSet<>(req.getPhases());

    // Setup (unconditional): activity -> canonical WA unit map (null when unmapped), and
    // activity -> Activity map for code/name reporting.
    List<Activity> acts = activityRepo.findByProjectId(projectId);
    Map<UUID, Activity> actById = acts.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    List<UUID> waIds = acts.stream()
        .map(Activity::getWorkActivityId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<UUID, WorkActivity> waById = waIds.isEmpty()
        ? new LinkedHashMap<>()
        : workActivityRepo.findAllById(waIds).stream()
            .collect(Collectors.toMap(WorkActivity::getId, wa -> wa, (a, b) -> a, LinkedHashMap::new));

    Map<UUID, String> canonicalByActivity = new HashMap<>();
    for (Activity a : acts) {
      WorkActivity wa = a.getWorkActivityId() == null ? null : waById.get(a.getWorkActivityId());
      canonicalByActivity.put(a.getId(),
          wa == null ? null : UnitNormalizer.canonicalLabel(wa.getDefaultUnit()));
    }

    List<Sample> samples = new ArrayList<>();
    Anchor anchor = new Anchor(0, 0);
    Dpr dpr = new Dpr(0, 0, 0, 0, 0);
    Boq boq = new Boq(0, 0, 0, 0, 0);
    List<UnmappedActivity> unmapped = new ArrayList<>();
    List<BoqConflict> boqConflicts = new ArrayList<>();

    if (phases.contains("ANCHOR")) {
      anchor = runAnchorPhase(waById, waIds, dry, samples);
      if (!dry && (anchor.workActivitiesNormalized() > 0 || anchor.normsNormalized() > 0)) {
        auditService.logUpdate("Project", projectId, "unit-repair", null,
            "ANCHOR:workActivitiesNormalized=" + anchor.workActivitiesNormalized()
                + ",normsNormalized=" + anchor.normsNormalized());
      }
    }

    if (phases.contains("DPR")) {
      dpr = runDprPhase(projectId, canonicalByActivity, actById, dry, chunkSize, samples, unmapped);
      if (!dry && dpr.relabeled() > 0) {
        auditService.logUpdate("Project", projectId, "unit-repair", null,
            "DPR:relabeled=" + dpr.relabeled());
      }
    }

    if (phases.contains("BOQ")) {
      boq = runBoqPhase(projectId, canonicalByActivity, dry, chunkSize, samples, boqConflicts);
      if (!dry && boq.relabeled() > 0) {
        auditService.logUpdate("Project", projectId, "unit-repair", null,
            "BOQ:relabeled=" + boq.relabeled());
      }
    }

    log.info("[UnitConsistencyRepairService] project={} dryRun={} phases={} anchor={} dpr={} boq={}",
        projectId, dry, phases, anchor, dpr, boq);

    return new UnitConsistencyRepairResponse(
        dry, new Summary(anchor, dpr, boq), unmapped, boqConflicts, samples);
  }

  /**
   * Phase ANCHOR: normalize each distinct WorkActivity's {@code defaultUnit} to its canonical
   * spelling, then normalize every ProductivityNorm of those WorkActivities to match. Meaning-
   * preserving only — a WA unit that isn't a known synonym passes through untouched.
   */
  private Anchor runAnchorPhase(Map<UUID, WorkActivity> waById, List<UUID> waIds,
                                 boolean dry, List<Sample> samples) {
    int workActivitiesNormalized = 0;
    for (WorkActivity wa : waById.values()) {
      String canonical = UnitNormalizer.canonicalLabel(wa.getDefaultUnit());
      if (canonical != null && !canonical.equals(wa.getDefaultUnit())) {
        String oldUnit = wa.getDefaultUnit();
        if (!dry) {
          wa.setDefaultUnit(canonical);
          self.saveWorkActivity(wa);
        }
        addSample(samples, "WORK_ACTIVITY", wa.getId().toString(), oldUnit, canonical);
        workActivitiesNormalized++;
      }
    }

    int normsNormalized = 0;
    if (!waIds.isEmpty()) {
      for (ProductivityNorm norm : productivityNormRepo.findByWorkActivityIdIn(waIds)) {
        // norm.getWorkActivity() is a LAZY proxy; .getId() reads the FK column without
        // initializing it, so this is safe regardless of spring.jpa.open-in-view.
        UUID normWaId = norm.getWorkActivity() == null ? null : norm.getWorkActivity().getId();
        WorkActivity wa = normWaId == null ? null : waById.get(normWaId);
        if (wa == null) {
          continue;
        }
        String canonical = UnitNormalizer.canonicalLabel(wa.getDefaultUnit());
        if (canonical != null && !canonical.equals(norm.getUnit())) {
          String oldUnit = norm.getUnit();
          if (!dry) {
            norm.setUnit(canonical);
            self.saveNorm(norm);
          }
          addSample(samples, "NORM", norm.getId().toString(), oldUnit, canonical);
          normsNormalized++;
        }
      }
    }

    return new Anchor(workActivitiesNormalized, normsNormalized);
  }

  /**
   * Phase DPR: relabel every DPR whose stored unit isn't exactly its activity's canonical unit,
   * grouped by target canonical and applied as id-chunked bulk updates. Activities with no
   * resolvable canonical unit are skipped and reported once (with their DPR count) in
   * {@code unmappedOut}.
   */
  private Dpr runDprPhase(UUID projectId, Map<UUID, String> canonicalByActivity,
                           Map<UUID, Activity> actById, boolean dry, int chunkSize,
                           List<Sample> samples, List<UnmappedActivity> unmappedOut) {
    List<Object[]> rows = dprRepo.findIdActivityUnitByProjectId(projectId);
    int scanned = rows.size();
    int skippedNoActivity = 0;
    int skippedUnmapped = 0;
    int alreadyConsistent = 0;

    Map<UUID, Integer> unmappedCounts = new LinkedHashMap<>();
    Map<String, List<UUID>> toRelabel = new LinkedHashMap<>();
    Map<UUID, String> oldUnitById = new HashMap<>();

    for (Object[] row : rows) {
      UUID id = (UUID) row[0];
      UUID activityId = (UUID) row[1];
      String unit = (String) row[2];

      if (activityId == null) {
        skippedNoActivity++;
        continue;
      }
      String canonical = canonicalByActivity.get(activityId);
      if (canonical == null) {
        skippedUnmapped++;
        unmappedCounts.merge(activityId, 1, Integer::sum);
        continue;
      }
      if (canonical.equals(unit)) {
        alreadyConsistent++;
        continue;
      }
      toRelabel.computeIfAbsent(canonical, k -> new ArrayList<>()).add(id);
      oldUnitById.put(id, unit);
    }

    for (Map.Entry<UUID, Integer> e : unmappedCounts.entrySet()) {
      Activity act = actById.get(e.getKey());
      unmappedOut.add(new UnmappedActivity(e.getKey(),
          act == null ? null : act.getCode(),
          act == null ? null : act.getName(),
          e.getValue()));
    }

    int relabeled = 0;
    for (Map.Entry<String, List<UUID>> e : toRelabel.entrySet()) {
      String canonical = e.getKey();
      for (List<UUID> chunk : partition(e.getValue(), chunkSize)) {
        relabeled += dry ? chunk.size() : self.bulkSetDprUnit(chunk, canonical);
        for (UUID id : chunk) {
          addSample(samples, "DPR", id.toString(), oldUnitById.get(id), canonical);
        }
      }
    }

    return new Dpr(scanned, relabeled, alreadyConsistent, skippedUnmapped, skippedNoActivity);
  }

  /**
   * Phase BOQ: conform every BOQ item in the project (not just ones referenced by a DPR) to its
   * linked activities' canonical unit. The population is the project's full BOQ so an item with
   * zero DPR references still appears in {@code scanned} (reported as {@code skippedUnused})
   * instead of silently vanishing, and a stale/phantom id from the DPR pairs can't be over-counted.
   * A BOQ item whose linked (mapped) activities disagree on the canonical unit is skipped and
   * reported as a conflict rather than guessed.
   */
  private Boq runBoqPhase(UUID projectId, Map<UUID, String> canonicalByActivity, boolean dry,
                           int chunkSize, List<Sample> samples, List<BoqConflict> conflictsOut) {
    List<Object[]> pairs = dprRepo.findDistinctBoqItemActivityPairsByProjectId(projectId);

    Map<UUID, Set<String>> canonUnitsByBoq = new LinkedHashMap<>();
    for (Object[] p : pairs) {
      UUID boqItemId = (UUID) p[0];
      UUID activityId = (UUID) p[1];
      String canonical = canonicalByActivity.get(activityId);
      if (canonical == null) {
        continue;
      }
      canonUnitsByBoq.computeIfAbsent(boqItemId, k -> new LinkedHashSet<>()).add(canonical);
    }

    List<BoqItem> boqItems = boqItemRepo.findByProjectId(projectId);

    int scanned = boqItems.size();
    int skippedUnused = 0;
    int skippedConflict = 0;
    int alreadyConsistent = 0;
    Map<String, List<UUID>> toRelabel = new LinkedHashMap<>();
    Map<UUID, String> currentUnitById = new HashMap<>();

    for (BoqItem item : boqItems) {
      UUID boqItemId = item.getId();
      String currentUnit = item.getUnit();
      currentUnitById.put(boqItemId, currentUnit);
      Set<String> canonSet = canonUnitsByBoq.getOrDefault(boqItemId, Set.of());

      if (canonSet.isEmpty()) {
        skippedUnused++;
        continue;
      }
      if (canonSet.size() > 1) {
        skippedConflict++;
        conflictsOut.add(new BoqConflict(boqItemId, item.getItemNo(), currentUnit,
            canonSet.stream().sorted().toList()));
        continue;
      }
      String canonical = canonSet.iterator().next();
      if (canonical.equals(currentUnit)) {
        alreadyConsistent++;
        continue;
      }
      toRelabel.computeIfAbsent(canonical, k -> new ArrayList<>()).add(boqItemId);
    }

    int relabeled = 0;
    for (Map.Entry<String, List<UUID>> e : toRelabel.entrySet()) {
      String canonical = e.getKey();
      for (List<UUID> chunk : partition(e.getValue(), chunkSize)) {
        relabeled += dry ? chunk.size() : self.bulkSetBoqUnit(chunk, canonical);
        for (UUID id : chunk) {
          addSample(samples, "BOQ", id.toString(), currentUnitById.get(id), canonical);
        }
      }
    }

    return new Boq(scanned, relabeled, alreadyConsistent, skippedConflict, skippedUnused);
  }

  private static void addSample(List<Sample> samples, String kind, String id, String from, String to) {
    if (samples.size() < MAX_SAMPLES) {
      samples.add(new Sample(kind, id, from, to));
    }
  }

  private static List<List<UUID>> partition(List<UUID> ids, int chunkSize) {
    List<List<UUID>> chunks = new ArrayList<>();
    for (int i = 0; i < ids.size(); i += chunkSize) {
      chunks.add(ids.subList(i, Math.min(i + chunkSize, ids.size())));
    }
    return chunks;
  }

  // ---- Per-chunk transaction methods, called through the self proxy. Never invoked in dryRun. ----

  @Transactional
  public int bulkSetDprUnit(List<UUID> ids, String unit) {
    return dprRepo.bulkSetUnit(ids, unit);
  }

  @Transactional
  public int bulkSetBoqUnit(List<UUID> ids, String unit) {
    return boqItemRepo.bulkSetUnit(ids, unit);
  }

  @Transactional
  public void saveWorkActivity(WorkActivity wa) {
    workActivityRepo.save(wa);
  }

  @Transactional
  public void saveNorm(ProductivityNorm norm) {
    productivityNormRepo.save(norm);
  }
}
