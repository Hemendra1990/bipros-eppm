package com.bipros.project.application.service;

import com.bipros.common.event.VoLineItemPayload;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.BoqItemResponse;
import com.bipros.project.application.dto.BoqSummaryResponse;
import com.bipros.project.application.dto.CreateBoqItemRequest;
import com.bipros.project.application.dto.UpdateBoqItemRequest;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.BoqOperation;
import com.bipros.project.domain.model.BoqStatus;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.BoqOperationRepository;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BoqService {

  private static final int RATIO_SCALE = 6;

  private final BoqItemRepository boqItemRepository;
  private final BoqOperationRepository boqOperationRepository;
  private final BoqOperationProgressCalculator operationProgressCalculator;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;
  private final DailyProgressReportRepository dprRepository;

  /** Cross-schema lookup of {@code activity.activities.name} from {@code activityId}. */
  @PersistenceContext
  private EntityManager em;

  public BoqItemResponse createItem(UUID projectId, CreateBoqItemRequest request) {
    ensureProjectExists(projectId);
    if (boqItemRepository.existsByProjectIdAndItemNo(projectId, request.itemNo())) {
      throw new BusinessRuleException("DUPLICATE_BOQ_ITEM",
          "BOQ item " + request.itemNo() + " already exists for project " + projectId);
    }
    BoqItem item = BoqItem.builder()
        .projectId(projectId)
        .itemNo(request.itemNo())
        .description(request.description())
        .unit(request.unit())
        .wbsNodeId(request.wbsNodeId())
        .boqQty(request.boqQty())
        .boqRate(request.boqRate())
        .budgetedRate(request.budgetedRate())
        .qtyExecutedToDate(request.qtyExecutedToDate())
        .actualRate(request.actualRate())
        .chapter(request.chapter())
        .status(request.status())
        .build();
    BoqCalculator.recompute(item);
    applyAutoStatus(item);
    BoqItem saved = boqItemRepository.save(item);
    auditService.logCreate("BoqItem", saved.getId(), BoqItemResponse.from(saved));
    return BoqItemResponse.from(saved);
  }

  public List<BoqItemResponse> createItemsBulk(UUID projectId, List<CreateBoqItemRequest> requests) {
    ensureProjectExists(projectId);
    // B6 (Stage 4): reject duplicate itemNos — in-batch and against existing rows — with the
    // offending numbers listed. This is the path the future importer uses; without the check
    // the batch dies on the DB unique constraint with an unreadable error.
    java.util.Set<String> seen = new java.util.HashSet<>();
    List<String> duplicates = new ArrayList<>();
    for (CreateBoqItemRequest r : requests) {
      if (!seen.add(r.itemNo()) || boqItemRepository.existsByProjectIdAndItemNo(projectId, r.itemNo())) {
        duplicates.add(r.itemNo());
      }
    }
    if (!duplicates.isEmpty()) {
      throw new BusinessRuleException("DUPLICATE_BOQ_ITEM",
          "Duplicate BOQ item number(s): " + String.join(", ", duplicates));
    }
    log.info("Bulk-creating {} BOQ items for project {}", requests.size(), projectId);
    List<BoqItem> items = requests.stream()
        .map(r -> {
          BoqItem item = BoqItem.builder()
              .projectId(projectId)
              .itemNo(r.itemNo())
              .description(r.description())
              .unit(r.unit())
              .wbsNodeId(r.wbsNodeId())
              .boqQty(r.boqQty())
              .boqRate(r.boqRate())
              .budgetedRate(r.budgetedRate())
              .qtyExecutedToDate(r.qtyExecutedToDate())
              .actualRate(r.actualRate())
              .chapter(r.chapter())
              .status(r.status())
              .build();
          BoqCalculator.recompute(item);
          applyAutoStatus(item);
          return item;
        })
        .toList();
    return boqItemRepository.saveAll(items).stream()
        .map(BoqItemResponse::from)
        .toList();
  }

  public BoqItemResponse updateItem(UUID projectId, UUID itemId, UpdateBoqItemRequest request) {
    BoqItem item = find(projectId, itemId);
    if (request.description() != null) item.setDescription(request.description());
    if (request.unit() != null) item.setUnit(request.unit());
    if (Boolean.TRUE.equals(request.clearWbsNode())) {
      item.setWbsNodeId(null);
    } else if (request.wbsNodeId() != null) {
      item.setWbsNodeId(request.wbsNodeId());
    }
    if (request.boqQty() != null) item.setBoqQty(request.boqQty());
    if (request.boqRate() != null) item.setBoqRate(request.boqRate());
    if (request.budgetedRate() != null) item.setBudgetedRate(request.budgetedRate());
    if (request.qtyExecutedToDate() != null) {
      // Review fix: on a split line the executed qty is DERIVED from operations — a manual
      // write would be silently reverted by the next roll-up and desynchronise billing.
      if (item.getSplitMode() != null) {
        throw new BusinessRuleException("BOQ_SPLIT_QTY_DERIVED",
            "Executed quantity on a split line is derived from its operations — record it "
                + "through DPRs against the operations.");
      }
      item.setQtyExecutedToDate(request.qtyExecutedToDate());
    }
    if (request.actualRate() != null) {
      item.setActualRate(request.actualRate());
      // Workstream B2: any explicit PATCH of actualRate is a manual override — the auto-recalc
      // listener (BoqActualRateRecalcListener) must skip this row from then on.
      item.setManualOverride(Boolean.TRUE);
    }
    if (request.chapter() != null) item.setChapter(request.chapter());
    if (request.status() != null) item.setStatus(request.status());
    BoqCalculator.recompute(item);
    applyAutoStatus(item);
    BoqItem saved = boqItemRepository.save(item);
    // Review fix (B7 parity): a split line's earnedFraction depends on boqQty — the VO path
    // re-runs the canonical roll-up on a qty revision, so the direct PATCH path must too.
    if (request.boqQty() != null && item.getSplitMode() != null) {
      syncMeasureTargetToQty(item);
      recomputeExecutedQtyApproved(projectId, itemId);
    }
    auditService.logUpdate("BoqItem", itemId, "boqItem", item, BoqItemResponse.from(saved));
    return BoqItemResponse.from(saved);
  }

  /**
   * DPR integration hook: update executed qty for a BOQ item matched by itemNo. Cumulative
   * quantity is added, not overwritten, so repeated daily syncs sum correctly.
   */
  public void addExecutedQty(UUID projectId, String itemNo, BigDecimal deltaQty) {
    if (deltaQty == null || deltaQty.signum() == 0) {
      return;
    }
    boqItemRepository.findByProjectIdAndItemNo(projectId, itemNo).ifPresent(item -> {
      BigDecimal current = item.getQtyExecutedToDate() != null ? item.getQtyExecutedToDate() : BigDecimal.ZERO;
      item.setQtyExecutedToDate(current.add(deltaQty));
      BoqCalculator.recompute(item);
      applyAutoStatus(item);
      boqItemRepository.save(item);
    });
  }

  /**
   * Inverse of {@link #addExecutedQty}: used by the DPR mutation listener when a DPR row is
   * deleted, edited to a smaller qty, or re-pointed to a different BOQ item. Floors at zero
   * to defend against legacy data drift where stored qty would otherwise go negative.
   */
  public void subtractExecutedQty(UUID projectId, String itemNo, BigDecimal deltaQty) {
    if (deltaQty == null || deltaQty.signum() == 0) {
      return;
    }
    boqItemRepository.findByProjectIdAndItemNo(projectId, itemNo).ifPresent(item -> {
      BigDecimal current = item.getQtyExecutedToDate() != null ? item.getQtyExecutedToDate() : BigDecimal.ZERO;
      BigDecimal next = current.subtract(deltaQty);
      if (next.signum() < 0) {
        next = BigDecimal.ZERO;
      }
      item.setQtyExecutedToDate(next);
      BoqCalculator.recompute(item);
      applyAutoStatus(item);
      boqItemRepository.save(item);
    });
  }

  /**
   * From-scratch approved-only qty recompute for one BOQ item. Sets
   * {@code qtyExecutedToDate} to the sum of {@code qty_executed} across all APPROVED DPRs linked
   * to this item, then runs {@link BoqCalculator#recompute} and saves. This eliminates the
   * delta-accumulation drift class of bugs: every call produces the same result regardless of
   * prior state.
   *
   * <p>Does NOT recompute {@code actualRate} — that is owned by
   * {@code BoqActualRateRecalcListener} (Workstream B2). No-op when the item no longer exists.
   * The existing add/subtract path does not check {@code manualOverride}, so this method does
   * not either (both paths own qty, not rate).
   */
  public void recomputeExecutedQtyApproved(UUID projectId, UUID boqItemId) {
    // Row lock (edge 17/X8): DPR approvals on sibling activities of one line serialise here.
    boqItemRepository.findByIdForUpdate(boqItemId).ifPresent(item -> {
      if (!item.getProjectId().equals(projectId)) return;
      List<BoqOperation> ops = boqOperationRepository.findByBoqItemIdOrderBySortOrderAscIdAsc(boqItemId);
      if (!ops.isEmpty()) {
        // Split line (§4.4/§4.5): per-operation approved sums → the two derived numbers.
        // Pre-split rows (null boq_operation_id) resolve to the legacy operation (§7.3).
        UUID legacyId = ops.stream()
            .filter(o -> Boolean.TRUE.equals(o.getIsLegacy()))
            .map(BoqOperation::getId)
            .findFirst().orElse(null);
        java.util.Map<UUID, BigDecimal> executed = new java.util.HashMap<>();
        for (Object[] row : dprRepository.sumQtyExecutedByOperationApproved(projectId, boqItemId)) {
          UUID opId = row[0] != null ? (UUID) row[0] : legacyId;
          if (opId == null) {
            // Should be unreachable (split() blocks pending null-op DPRs and lines with
            // history get a legacy op) — but a dropped quantity must never be silent.
            log.warn("[BoqService] split line {} has {} approved qty with no operation and no "
                + "legacy op — EXCLUDED from the roll-up; investigate", boqItemId, row[1]);
            continue;
          }
          BigDecimal qty = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
          executed.merge(opId, qty, BigDecimal::add);
        }
        var snapshots = ops.stream().map(o -> new BoqOperationProgressCalculator.OpSnapshot(
            o.getId(), o.getTargetQty(), o.getWeightPct(),
            Boolean.TRUE.equals(o.getIsMeasure()), Boolean.TRUE.equals(o.getIsLegacy()),
            executed.getOrDefault(o.getId(), BigDecimal.ZERO))).toList();
        var result = operationProgressCalculator.compute(item.getSplitMode(), item.getBoqQty(), snapshots);
        item.setQtyExecutedToDate(result.measuredQty());
        item.setEarnedFraction(result.earnedFraction());
      } else {
        BigDecimal approvedQty = dprRepository.sumQtyExecutedByBoqItemIdApproved(projectId, boqItemId);
        item.setQtyExecutedToDate(approvedQty != null ? approvedQty : BigDecimal.ZERO);
        item.setEarnedFraction(null);
      }
      BoqCalculator.recompute(item);
      applyAutoStatus(item);
      boqItemRepository.save(item);
    });
  }

  /**
   * Auto-transition BOQ status from progress:
   * <ul>
   *   <li>qtyExecutedToDate &gt; boqQty → OVERRUN (regardless of percent — the unbillable-without-VO state)</li>
   *   <li>percent ≥ 100% AND qtyExecutedToDate ≤ boqQty → COMPLETED</li>
   *   <li>0 &lt; percent &lt; 100% → ACTIVE</li>
   *   <li>percent = 0 or null → PENDING</li>
   * </ul>
   * {@link BoqStatus#ON_HOLD} is a manual state — once set by the caller it stays until an
   * explicit change. PMS MasterData Screen 03 description: "Status auto-updates".
   */
  static void applyAutoStatus(BoqItem item) {   // package-private: BoqRebuildService shares it
    if (item.getStatus() == BoqStatus.ON_HOLD) return;
    BigDecimal qty = item.getQtyExecutedToDate();
    BigDecimal boqQty = item.getBoqQty();
    boolean overrun = qty != null && boqQty != null && qty.compareTo(boqQty) > 0;
    if (overrun) {
      item.setStatus(BoqStatus.OVERRUN);
      return;
    }
    BigDecimal pct = item.getPercentComplete();
    if (pct == null || pct.signum() == 0) {
      item.setStatus(BoqStatus.PENDING);
    } else if (pct.compareTo(BigDecimal.ONE) >= 0) {
      item.setStatus(BoqStatus.COMPLETED);
    } else {
      item.setStatus(BoqStatus.ACTIVE);
    }
  }

  public void deleteItem(UUID projectId, UUID itemId) {
    BoqItem item = find(projectId, itemId);
    rejectIfReferenced(item);
    boqItemRepository.delete(item);
    auditService.logDelete("BoqItem", itemId);
  }

  /**
   * B5 (Stage 4): a line referenced by DPRs, operations or linked activities must not vanish —
   * every aggregate reading it would silently shrink, and the pilot's 408 orphaned
   * {@code ra_bill_items} prove orphans happen. Changeset 130 adds the DB-side FK for prod.
   */
  private void rejectIfReferenced(BoqItem item) {
    long dprs = ((Number) em.createNativeQuery(
            "SELECT count(*) FROM project.daily_progress_reports WHERE boq_item_id = :id")
        .setParameter("id", item.getId())
        .getSingleResult()).longValue();
    if (dprs > 0) {
      throw new BusinessRuleException("BOQ_DELETE_HAS_DPRS",
          dprs + " DPR(s) reference BOQ item " + item.getItemNo()
              + " — re-point or delete them first.");
    }
    if (boqOperationRepository.existsByBoqItemId(item.getId())) {
      throw new BusinessRuleException("BOQ_DELETE_HAS_OPERATIONS",
          "BOQ item " + item.getItemNo() + " is split into operations — unsplit it first.");
    }
    long activities = ((Number) em.createNativeQuery(
            "SELECT count(*) FROM activity.activities WHERE boq_item_id = :id")
        .setParameter("id", item.getId())
        .getSingleResult()).longValue();
    if (activities > 0) {
      throw new BusinessRuleException("BOQ_DELETE_HAS_ACTIVITIES",
          activities + " activity(ies) link to BOQ item " + item.getItemNo()
              + " — clear their BOQ link first.");
    }
  }

  /**
   * Apply a batch of {@link VoLineItemPayload} to BOQ rows for a project. Used by the VO
   * approval listener to mutate {@code BoqItem} state transactionally with the VO approval.
   * Returns the BoQ ids touched (incl. newly-created items) so the caller can audit-log them.
   *
   * <p>Each line is validated against the existing data — a REVISE_QTY/REVISE_RATE/DELETE_ITEM
   * referencing an unknown BoqItem fails fast (the listener should have caught this earlier),
   * and an ADD_ITEM with a duplicate {@code newItemNo} fails with the same "duplicate" error
   * shape used by {@link #createItem}. The transaction rollback then unwinds the VO approval.
   */
  public List<UUID> applyVoLineItems(UUID projectId, List<VoLineItemPayload> lineItems) {
    if (lineItems == null || lineItems.isEmpty()) return List.of();
    java.util.List<UUID> impacted = new java.util.ArrayList<>(lineItems.size());
    for (VoLineItemPayload li : lineItems) {
      switch (li.action()) {
        case ADD_ITEM -> impacted.add(applyAddItem(projectId, li));
        case REVISE_QTY -> impacted.add(applyReviseQty(projectId, li));
        case REVISE_RATE -> impacted.add(applyReviseRate(projectId, li));
        case DELETE_ITEM -> impacted.add(applyDeleteItem(projectId, li));
      }
    }
    return impacted;
  }

  private UUID applyAddItem(UUID projectId, VoLineItemPayload li) {
    if (boqItemRepository.existsByProjectIdAndItemNo(projectId, li.newItemNo())) {
      throw new BusinessRuleException("DUPLICATE_BOQ_ITEM",
          "BOQ item " + li.newItemNo() + " already exists for project " + projectId
              + " — VO ADD_ITEM cannot overwrite an existing row.");
    }
    BoqItem item = BoqItem.builder()
        .projectId(projectId)
        .itemNo(li.newItemNo())
        .description(li.newItemDescription() != null ? li.newItemDescription() : li.newItemNo())
        .unit(li.newItemUnit() != null ? li.newItemUnit() : "Each")
        .boqQty(li.revisedQty())
        .boqRate(li.revisedRate())
        .build();
    BoqCalculator.recompute(item);
    applyAutoStatus(item);
    BoqItem saved = boqItemRepository.save(item);
    auditService.logCreate("BoqItem", saved.getId(), BoqItemResponse.from(saved));
    // CC-6: tag the change as VO-applied so the audit log can be filtered.
    auditService.logUpdate("BoqItem", saved.getId(), "VO_APPLIED", null, BoqItemResponse.from(saved));
    return saved.getId();
  }

  private UUID applyReviseQty(UUID projectId, VoLineItemPayload li) {
    BoqItem item = requireBoqItem(projectId, li.boqItemId(), "REVISE_QTY");
    BoqItemResponse before = BoqItemResponse.from(item);
    BigDecimal previous = item.getBoqQty();
    item.setBoqQty(li.revisedQty());
    BoqCalculator.recompute(item);
    applyAutoStatus(item);
    BoqItem saved = boqItemRepository.save(item);
    // B7/X3 (Stage 4): a split line's earnedFraction depends on boqQty (partition denominator) —
    // re-run the canonical roll-up. Owner rule 2026-08-05: the weighted measurement operation's
    // target IS the contracted quantity, so it moves with the revision; other operation targets
    // stay untouched and are flagged for QS review in the VO audit trail.
    if (item.getSplitMode() != null) {
      syncMeasureTargetToQty(item);
      recomputeExecutedQtyApproved(projectId, item.getId());
      String note = "WEIGHTED_OPERATIONS".equals(item.getSplitMode())
          ? "boqQty revised on a split line — measurement-operation target moved to the new "
              + "quantity; other operation targets unchanged; review the split."
          : "boqQty revised on a partition split — operation targets unchanged and may no longer "
              + "sum to the line quantity; rebalance them in the Operations dialog.";
      auditService.logUpdate("BoqItem", item.getId(), "VO_SPLIT_NOTE", null, note);
    }
    auditService.logUpdate("BoqItem", item.getId(), "boqQty", previous, li.revisedQty());
    auditService.logUpdate("BoqItem", item.getId(), "VO_APPLIED", before, BoqItemResponse.from(saved));
    return saved.getId();
  }

  /**
   * Owner rule (2026-08-05): on a WEIGHTED split the measurement operation's target IS the
   * contracted quantity — when boqQty changes (inline PATCH or VO REVISE_QTY) the target moves
   * with it, keeping "% complete = 100 ⇔ billable qty = boqQty" true. Must run BEFORE the
   * canonical roll-up so the new target feeds the earned fraction. Partition targets are
   * deliberately NOT touched (the reweight validation re-enforces their sum on next edit).
   */
  private void syncMeasureTargetToQty(BoqItem item) {
    if (!"WEIGHTED_OPERATIONS".equals(item.getSplitMode())) return;
    BigDecimal qty = item.getBoqQty();
    if (qty == null || qty.signum() <= 0) return;
    boqOperationRepository.findByBoqItemIdOrderBySortOrderAscIdAsc(item.getId()).stream()
        .filter(op -> Boolean.TRUE.equals(op.getIsMeasure()))
        .forEach(op -> {
          op.setTargetQty(qty);
          boqOperationRepository.save(op);
        });
  }

  private UUID applyReviseRate(UUID projectId, VoLineItemPayload li) {
    BoqItem item = requireBoqItem(projectId, li.boqItemId(), "REVISE_RATE");
    BoqItemResponse before = BoqItemResponse.from(item);
    BigDecimal previous = item.getBoqRate();
    item.setBoqRate(li.revisedRate());
    BoqCalculator.recompute(item);
    applyAutoStatus(item);
    BoqItem saved = boqItemRepository.save(item);
    auditService.logUpdate("BoqItem", item.getId(), "boqRate", previous, li.revisedRate());
    auditService.logUpdate("BoqItem", item.getId(), "VO_APPLIED", before, BoqItemResponse.from(saved));
    return saved.getId();
  }

  private UUID applyDeleteItem(UUID projectId, VoLineItemPayload li) {
    BoqItem item = requireBoqItem(projectId, li.boqItemId(), "DELETE_ITEM");
    rejectIfReferenced(item);   // B5 — same guard as the direct delete; rolls the VO back
    BoqItemResponse before = BoqItemResponse.from(item);
    boqItemRepository.delete(item);
    auditService.logDelete("BoqItem", item.getId());
    // CC-6: full before-state snapshot so the deleted row is still recoverable from the audit log.
    auditService.logUpdate("BoqItem", item.getId(), "VO_APPLIED", before, null);
    return item.getId();
  }

  private BoqItem requireBoqItem(UUID projectId, UUID boqItemId, String actionForMessage) {
    BoqItem item = boqItemRepository.findById(boqItemId)
        .orElseThrow(() -> new BusinessRuleException(
            "VO_LINE_BOQ_NOT_FOUND",
            actionForMessage + " line item references unknown BoqItem " + boqItemId));
    if (!item.getProjectId().equals(projectId)) {
      throw new BusinessRuleException(
          "VO_LINE_BOQ_PROJECT_MISMATCH",
          actionForMessage + " line item references BoqItem " + boqItemId
              + " from a different project (" + item.getProjectId() + " vs " + projectId + ")");
    }
    return item;
  }

  @Transactional(readOnly = true)
  public BoqItemResponse getItem(UUID projectId, UUID itemId) {
    return BoqItemResponse.from(find(projectId, itemId));
  }

  @Transactional(readOnly = true)
  public BoqSummaryResponse getProjectBoqSummary(UUID projectId) {
    ensureProjectExists(projectId);
    List<BoqItem> items = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);

    BigDecimal boqTotal = BigDecimal.ZERO;
    BigDecimal budgetedTotal = BigDecimal.ZERO;
    BigDecimal actualTotal = BigDecimal.ZERO;
    BigDecimal earnedBudgetTotal = BigDecimal.ZERO;
    BigDecimal cappedEarnedTotal = BigDecimal.ZERO;

    for (BoqItem i : items) {
      boqTotal = boqTotal.add(nz(i.getBoqAmount()));
      budgetedTotal = budgetedTotal.add(nz(i.getBudgetedAmount()));
      actualTotal = actualTotal.add(nz(i.getActualAmount()));
      // Gate A (04 Aug 2026): the grand variance basis is the same capped/split-aware
      // earnedBudget each row's costVariance uses, so Σ(rows) always equals the footer.
      earnedBudgetTotal = earnedBudgetTotal.add(BoqCalculator.earnedBudget(i));
      cappedEarnedTotal = cappedEarnedTotal.add(BoqCalculator.cappedEarned(
          i.getEarnedFraction(), i.getQtyExecutedToDate(), i.getBoqQty(), i.getBudgetedRate()));
    }

    BigDecimal grandVariance = actualTotal.subtract(earnedBudgetTotal).setScale(2, RoundingMode.HALF_UP);
    BigDecimal grandVariancePct = earnedBudgetTotal.signum() == 0
        ? null
        : grandVariance.divide(earnedBudgetTotal, RATIO_SCALE, RoundingMode.HALF_UP);
    BigDecimal overallPct = budgetedTotal.signum() == 0
        ? null
        : cappedEarnedTotal.divide(budgetedTotal, RATIO_SCALE, RoundingMode.HALF_UP);
    if (overallPct != null && overallPct.compareTo(BigDecimal.ONE) > 0) overallPct = BigDecimal.ONE;

    List<BoqItemResponse> responses = items.stream().map(BoqItemResponse::from).toList();
    return new BoqSummaryResponse(
        responses,
        boqTotal.setScale(2, RoundingMode.HALF_UP),
        budgetedTotal.setScale(2, RoundingMode.HALF_UP),
        actualTotal.setScale(2, RoundingMode.HALF_UP),
        grandVariance,
        grandVariancePct,
        overallPct);
  }

  private BoqItem find(UUID projectId, UUID itemId) {
    BoqItem item = boqItemRepository.findById(itemId)
        .orElseThrow(() -> new ResourceNotFoundException("BoqItem", itemId));
    if (!item.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("BoqItem", itemId);
    }
    return item;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  /**
   * BOQ candidates for a given activity. Used by the DPR form to pre-select / suggest a BOQ
   * link when an activity is picked. Heuristic: read the activity's {@code name} via a
   * cross-schema query, then filter the project's BOQ items by substring/contains match
   * against {@code description} (same heuristic the legacy
   * {@code DailyCostReportService.resolveBoqItem} used). Empty list if no activity, no match,
   * or no BoQ defined yet.
   */
  @Transactional(readOnly = true)
  public List<BoqItemResponse> listForActivity(UUID projectId, UUID activityId) {
    ensureProjectExists(projectId);
    if (activityId == null) return List.of();

    // Single-column native query → JPA returns List<String> (scalar), not List<Object[]>.
    // Indexing into a scalar row with [0] threw ClassCastException at runtime.
    @SuppressWarnings("unchecked")
    List<Object> nameRows = em.createNativeQuery(
            "SELECT name FROM activity.activities WHERE id = :id LIMIT 1")
        .setParameter("id", activityId)
        .getResultList();
    if (nameRows.isEmpty()) return List.of();
    Object first = nameRows.get(0);
    String activityName = first == null ? null : first.toString();
    if (activityName == null || activityName.isBlank()) return List.of();

    List<BoqItem> all = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);
    String needle = activityName.toLowerCase(Locale.ROOT);
    List<BoqItemResponse> matches = new ArrayList<>();
    for (BoqItem b : all) {
      if (b.getDescription() == null) continue;
      String desc = b.getDescription().toLowerCase(Locale.ROOT);
      if (desc.startsWith(needle) || desc.contains(needle)
          || needle.contains(desc.split("[(\\-]")[0].trim())) {
        matches.add(BoqItemResponse.from(b));
      }
    }
    return matches;
  }
}
