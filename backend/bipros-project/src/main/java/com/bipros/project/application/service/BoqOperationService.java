package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.BoqOperationDto;
import com.bipros.project.application.dto.SplitBoqItemRequest;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.BoqOperation;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.BoqOperationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Split lifecycle of a BOQ line (split design §4/§7): split into operations, reweight (D5 freeze),
 * unsplit. Operations carry no money (D1) — every write here ends with the line's canonical
 * from-scratch recompute so {@code earnedFraction}/{@code qtyExecutedToDate} are consistent in the
 * same transaction (A2). Activity re-pointing (L1) is a native cross-schema write, same pattern as
 * the Stage-3 backfill.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BoqOperationService {

  static final String MODE_WEIGHTED = "WEIGHTED_OPERATIONS";
  static final String MODE_PARTITION = "QUANTITY_PARTITION";
  static final String LEGACY_OP_CODE = "LEGACY";
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");
  /** Rounding slack for Σ partition targets vs boqQty (values are numeric(18,3)). */
  private static final BigDecimal QTY_TOLERANCE = new BigDecimal("0.01");

  private final BoqItemRepository boqItemRepository;
  private final BoqOperationRepository operationRepository;
  private final BoqOperationProgressCalculator progressCalculator;
  private final BoqService boqService;
  private final AuditService auditService;

  @PersistenceContext
  private EntityManager em;

  @Transactional(readOnly = true)
  public List<BoqOperationDto> list(UUID projectId, UUID itemId) {
    BoqItem item = find(projectId, itemId);
    List<BoqOperation> ops = operationRepository.findByBoqItemIdOrderBySortOrderAscIdAsc(item.getId());
    if (ops.isEmpty()) return List.of();
    Map<UUID, BigDecimal> executed = executedByOperation(projectId, item.getId(), ops);
    return ops.stream()
        .map(op -> BoqOperationDto.from(op, executed.getOrDefault(op.getId(), BigDecimal.ZERO)))
        .toList();
  }

  public List<BoqOperationDto> split(UUID projectId, UUID itemId, SplitBoqItemRequest request) {
    BoqItem item = find(projectId, itemId);
    if (operationRepository.existsByBoqItemId(item.getId())) {
      throw new BusinessRuleException("BOQ_ALREADY_SPLIT",
          "BOQ item " + item.getItemNo() + " is already split — use reweight or unsplit first.");
    }
    String mode = request.splitMode();
    if (!MODE_WEIGHTED.equals(mode) && !MODE_PARTITION.equals(mode)) {
      throw new BusinessRuleException("BOQ_SPLIT_MODE_INVALID",
          "splitMode must be " + MODE_WEIGHTED + " or " + MODE_PARTITION + " (got " + mode + ")");
    }

    List<BoqOperationDto> requested = request.operations() != null ? request.operations() : List.of();
    if (requested.size() < 2) {
      throw new BusinessRuleException("BOQ_SPLIT_MIN_OPS",
          "A split needs at least 2 operations (got " + requested.size() + ").");
    }
    validateOpCodes(requested);
    validateOpFields(requested);
    boolean hasHistory = nz(item.getQtyExecutedToDate()).signum() > 0;
    if (MODE_WEIGHTED.equals(mode)) {
      validateSingleMeasure(requested, item);
      validateWeightsSum(requested, hasHistory ? request.legacyWeight() : null, hasHistory);
    } else {
      // Review fix: partition children divide the line's OWN quantity — a child in a different
      // unit would sum trips into m³ and corrupt billing/income/EV with no error anywhere.
      for (BoqOperationDto dto : requested) {
        String u = dto.unit() == null ? "" : dto.unit().trim();
        if (!u.equalsIgnoreCase(item.getUnit() == null ? "" : item.getUnit().trim())) {
          throw new BusinessRuleException("BOQ_SPLIT_PARTITION_UNIT",
              "Partition children divide the line's own quantity — each must use the line's unit ('"
                  + item.getUnit() + "', got '" + u + "').");
        }
      }
      validatePartitionTargets(item,
          requested.stream().map(BoqOperationDto::targetQty).toList(),
          hasHistory ? nz(item.getQtyExecutedToDate()) : BigDecimal.ZERO);
    }

    // Review fix (pre-split pending DPRs): a DRAFT/SUBMITTED DPR carries no operation; approved
    // AFTER the split it would land in the null-operation bucket and — with no legacy op to
    // absorb it — be silently dropped from the roll-up while still counting as income.
    long pending = ((Number) em.createNativeQuery(
            "SELECT count(*) FROM project.daily_progress_reports "
                + "WHERE boq_item_id = :itemId AND boq_operation_id IS NULL "
                + "AND (approval_status IS NULL OR approval_status IN ('DRAFT','SUBMITTED'))")
        .setParameter("itemId", item.getId())
        .getSingleResult()).longValue();
    if (pending > 0) {
      throw new BusinessRuleException("BOQ_SPLIT_PENDING_DPRS",
          pending + " DPR(s) against " + item.getItemNo() + " are still draft/submitted — approve "
              + "or reject them before splitting, or their quantity would become unattributable.");
    }
    // Review fix (imported/manual history): the legacy operation's executed qty resolves from
    // approved DPR sums, so stored qty NOT backed by approved DPRs would silently collapse to
    // the DPR-backed sum in this very transaction. Block until reconciled.
    Object approvedRaw = em.createNativeQuery(
            "SELECT COALESCE(SUM(qty_executed), 0) FROM project.daily_progress_reports "
                + "WHERE boq_item_id = :itemId AND approval_status = 'APPROVED'")
        .setParameter("itemId", item.getId())
        .getSingleResult();
    BigDecimal approvedSum = new BigDecimal(approvedRaw.toString());
    if (nz(item.getQtyExecutedToDate()).compareTo(approvedSum) != 0) {
      throw new BusinessRuleException("BOQ_SPLIT_QTY_DIVERGENCE",
          "Stored executed qty (" + nz(item.getQtyExecutedToDate()) + ") on " + item.getItemNo()
              + " is not backed by approved DPRs (" + approvedSum + "). Manually-entered or "
              + "imported history would be lost by a split — reconcile it first.");
    }

    List<BoqOperation> ops = new ArrayList<>();
    if (hasHistory) {
      // §7.3: the legacy operation absorbs all pre-split DPR history (rows with a null
      // boq_operation_id resolve to it at recompute time). Its target = what was already done,
      // so its own completion is 1.0 and the QS-chosen legacyWeight prices that history.
      ops.add(BoqOperation.builder()
          .projectId(projectId)
          .boqItemId(item.getId())
          .opCode(LEGACY_OP_CODE)
          .name("Pre-split history")
          .unit(item.getUnit())
          .targetQty(item.getQtyExecutedToDate())
          .weightPct(MODE_WEIGHTED.equals(mode) ? request.legacyWeight() : BigDecimal.ZERO)
          .isMeasure(Boolean.FALSE)
          .isLegacy(Boolean.TRUE)
          .sortOrder(0)
          .build());
    }
    int order = 1;
    for (BoqOperationDto dto : requested) {
      ops.add(BoqOperation.builder()
          .projectId(projectId)
          .boqItemId(item.getId())
          .opCode(dto.opCode().trim())
          .name(dto.name() != null && !dto.name().isBlank() ? dto.name() : dto.opCode().trim())
          .unit(dto.unit())
          .targetQty(dto.targetQty())
          .weightPct(nz(dto.weightPct()))
          .isMeasure(MODE_WEIGHTED.equals(mode) && Boolean.TRUE.equals(dto.isMeasure()))
          .isLegacy(Boolean.FALSE)
          .sortOrder(dto.sortOrder() != null ? dto.sortOrder() : order)
          .build());
      order++;
    }
    List<BoqOperation> saved = operationRepository.saveAll(ops);

    repointActivities(item, saved, request.activityAssignments());

    item.setSplitMode(mode);
    boqItemRepository.save(item);
    boqService.recomputeExecutedQtyApproved(projectId, item.getId());   // A2: same-txn recompute
    auditService.logUpdate("BoqItem", item.getId(), "split", null, describe(saved));
    log.info("Split BOQ item {} ({}) into {} operations, mode={}",
        item.getItemNo(), item.getId(), saved.size(), mode);
    Map<UUID, BigDecimal> executed = executedByOperation(projectId, item.getId(), saved);
    return saved.stream()
        .map(op -> BoqOperationDto.from(op, executed.getOrDefault(op.getId(), BigDecimal.ZERO)))
        .toList();
  }

  /**
   * Update the existing operation set's definitions, matched by {@code opCode} — no add/remove
   * (unsplit and re-split while nothing is attributed; afterwards the set is part of history).
   * D5: once any DPR is attributed to an operation, or any approved DPR post-dates the split,
   * weights are frozen — changes then require a {@code reason} and are audited old→new.
   */
  public List<BoqOperationDto> reweight(UUID projectId, UUID itemId, SplitBoqItemRequest request) {
    BoqItem item = find(projectId, itemId);
    List<BoqOperation> ops = operationRepository.findByBoqItemIdOrderBySortOrderAscIdAsc(item.getId());
    if (ops.isEmpty()) {
      throw new BusinessRuleException("BOQ_NOT_SPLIT",
          "BOQ item " + item.getItemNo() + " is not split.");
    }
    Map<String, BoqOperation> byCode = new LinkedHashMap<>();
    ops.forEach(op -> byCode.put(op.getOpCode(), op));

    List<BoqOperationDto> requested = request.operations() != null ? request.operations() : List.of();
    for (BoqOperationDto dto : requested) {
      if (dto.opCode() == null || !byCode.containsKey(dto.opCode().trim())) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_SET_CHANGED",
            "Operation '" + dto.opCode() + "' does not exist on this line — reweight cannot add or "
                + "remove operations (unsplit and re-split while no DPRs are attributed).");
      }
    }

    boolean frozen = weightsFrozen(item, ops);
    Map<String, BigDecimal> oldWeights = new LinkedHashMap<>();
    ops.forEach(op -> oldWeights.put(op.getOpCode(), op.getWeightPct()));
    if (frozen && (request.reason() == null || request.reason().isBlank())) {
      throw new BusinessRuleException("BOQ_SPLIT_FROZEN",
          "Weights on " + item.getItemNo() + " are frozen (DPRs already recorded against the split) "
              + "— pass a reason to change them; the change is audited.");
    }

    // Apply the requested changes to the in-memory set, then validate the FINAL state.
    validateOpFields(requested);
    for (BoqOperationDto dto : requested) {
      BoqOperation op = byCode.get(dto.opCode().trim());
      boolean legacy = Boolean.TRUE.equals(op.getIsLegacy());
      if (dto.name() != null && !dto.name().isBlank()) op.setName(dto.name());
      // Review fix: the legacy op's unit/target are the frozen opening balance (§7.3) — only
      // its weight (the QS's pricing of history) may change.
      if (dto.unit() != null && !legacy) op.setUnit(dto.unit());
      if (dto.weightPct() != null) op.setWeightPct(dto.weightPct());
      if (!legacy) {
        op.setTargetQty(dto.targetQty());   // null = milestone, deliberate (non-measure ops)
      }
      if (dto.isMeasure() != null && !legacy) {
        op.setIsMeasure(dto.isMeasure());
      }
      if (dto.sortOrder() != null) op.setSortOrder(dto.sortOrder());
    }
    if (MODE_WEIGHTED.equals(item.getSplitMode())) {
      validateFinalState(item, ops);
    } else {
      // Partition final-state twin of the split-time unit + targets rules.
      for (BoqOperation op : ops) {
        String u = op.getUnit() == null ? "" : op.getUnit().trim();
        if (!u.equalsIgnoreCase(item.getUnit() == null ? "" : item.getUnit().trim())) {
          throw new BusinessRuleException("BOQ_SPLIT_PARTITION_UNIT",
              "Partition children must use the line's unit ('" + item.getUnit() + "').");
        }
      }
      validatePartitionTargets(item,
          ops.stream().filter(op -> !Boolean.TRUE.equals(op.getIsLegacy()))
              .map(BoqOperation::getTargetQty).toList(),
          ops.stream().filter(op -> Boolean.TRUE.equals(op.getIsLegacy()))
              .map(op -> nz(op.getTargetQty())).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // Edge 3: a reweight must not pull the BILLABLE quantity below what the employer was
    // already billed — the next RA-Bill delta would silently go negative and vanish
    // (delta<=0 rows are skipped). Review fix: the floor compares the measured/billing basis
    // (what RaBillDraftCalculator actually claims), not the earned-fraction/EV basis — weights
    // never move billing, but re-flagging the measurement operation does.
    BigDecimal floorQty = raBillCumulativeFloor(item.getId());
    if (floorQty != null && floorQty.signum() > 0 && nz(item.getBoqQty()).signum() > 0) {
      Map<UUID, BigDecimal> executed = executedByOperation(projectId, item.getId(), ops);
      var snapshots = ops.stream().map(op -> new BoqOperationProgressCalculator.OpSnapshot(
          op.getId(), op.getTargetQty(), op.getWeightPct(),
          Boolean.TRUE.equals(op.getIsMeasure()), Boolean.TRUE.equals(op.getIsLegacy()),
          executed.getOrDefault(op.getId(), BigDecimal.ZERO))).toList();
      BigDecimal claimable = progressCalculator
          .compute(item.getSplitMode(), item.getBoqQty(), snapshots).measuredQty()
          .min(item.getBoqQty());
      if (claimable.compareTo(floorQty) < 0) {
        throw new BusinessRuleException("BOQ_SPLIT_RA_BILL_FLOOR",
            "These changes would drop the billable quantity to " + claimable + ", below the "
                + floorQty + " already billed on an RA-Bill for " + item.getItemNo() + ".");
      }
    }

    List<BoqOperation> saved = operationRepository.saveAll(ops);
    boqService.recomputeExecutedQtyApproved(projectId, item.getId());   // A2
    Map<String, BigDecimal> newWeights = new LinkedHashMap<>();
    saved.forEach(op -> newWeights.put(op.getOpCode(), op.getWeightPct()));
    auditService.logUpdate("BoqItem", item.getId(),
        frozen ? "operationWeights[FROZEN:" + request.reason() + "]" : "operationWeights",
        oldWeights, newWeights);
    Map<UUID, BigDecimal> executed = executedByOperation(projectId, item.getId(), saved);
    return saved.stream()
        .map(op -> BoqOperationDto.from(op, executed.getOrDefault(op.getId(), BigDecimal.ZERO)))
        .toList();
  }

  /** Reversibility proof: only while zero DPRs are attributed to any operation. */
  public void unsplit(UUID projectId, UUID itemId) {
    BoqItem item = find(projectId, itemId);
    List<BoqOperation> ops = operationRepository.findByBoqItemIdOrderBySortOrderAscIdAsc(item.getId());
    if (ops.isEmpty()) {
      throw new BusinessRuleException("BOQ_NOT_SPLIT",
          "BOQ item " + item.getItemNo() + " is not split.");
    }
    if (attributedDprCount(item.getId()) > 0) {
      throw new BusinessRuleException("BOQ_UNSPLIT_HAS_DPRS",
          "DPRs are already attributed to operations of " + item.getItemNo()
              + " — revoke/re-point them before unsplitting.");
    }
    // Activities keep their line link; only the operation pointer clears.
    em.createNativeQuery(
            "UPDATE activity.activities SET boq_operation_id = NULL WHERE boq_item_id = :itemId")
        .setParameter("itemId", item.getId())
        .executeUpdate();
    // Review fix (unsplit race): a DPR save that read the op pointer just before this commit
    // would otherwise keep a dangling boq_operation_id that the income predicates exclude
    // forever. Clear any such reference before the operations disappear (also enforced by
    // fk_dpr_boq_operation, changeset 131, in prod).
    em.createNativeQuery(
            "UPDATE project.daily_progress_reports SET boq_operation_id = NULL "
                + "WHERE boq_operation_id IN "
                + "(SELECT id FROM project.boq_operations WHERE boq_item_id = :itemId)")
        .setParameter("itemId", item.getId())
        .executeUpdate();
    operationRepository.deleteAll(ops);
    item.setSplitMode(null);
    item.setEarnedFraction(null);
    boqItemRepository.save(item);
    boqService.recomputeExecutedQtyApproved(projectId, item.getId());   // back to the flat path
    auditService.logUpdate("BoqItem", item.getId(), "unsplit", describe(ops), null);
    log.info("Unsplit BOQ item {} ({}) — {} operations removed", item.getItemNo(), item.getId(), ops.size());
  }

  // ---------------------------------------------------------------------------------------------

  private void validateOpCodes(List<BoqOperationDto> requested) {
    Set<String> seen = new HashSet<>();
    for (BoqOperationDto dto : requested) {
      String code = dto.opCode() == null ? "" : dto.opCode().trim();
      if (code.isEmpty() || code.length() > 40) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "Every operation needs an opCode (1-40 chars).");
      }
      if (LEGACY_OP_CODE.equalsIgnoreCase(code)) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "'" + LEGACY_OP_CODE + "' is reserved for the auto-created history operation.");
      }
      if (!seen.add(code)) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "Duplicate operation code '" + code + "'.");
      }
    }
  }

  /** Review fix: field-level sanity shared by split and reweight — negative weights could push
   *  the earned fraction above 1 (Σw still 100), and over-length name/unit would die as a 500
   *  on the column limit instead of a readable error. */
  private void validateOpFields(List<BoqOperationDto> requested) {
    for (BoqOperationDto dto : requested) {
      if (dto.weightPct() != null && dto.weightPct().signum() < 0) {
        throw new BusinessRuleException("BOQ_SPLIT_WEIGHTS_SUM",
            "Operation weights cannot be negative (op '" + dto.opCode() + "').");
      }
      if (dto.targetQty() != null && dto.targetQty().signum() < 0) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "Operation target quantity cannot be negative (op '" + dto.opCode() + "').");
      }
      if (dto.name() != null && dto.name().length() > 200) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "Operation name is limited to 200 characters (op '" + dto.opCode() + "').");
      }
      if (dto.unit() != null && dto.unit().length() > 20) {
        throw new BusinessRuleException("BOQ_SPLIT_OP_CODE",
            "Operation unit is limited to 20 characters (op '" + dto.opCode() + "').");
      }
    }
  }

  /** D3: exactly one measurement operation, in the line's own unit. */
  private void validateSingleMeasure(List<BoqOperationDto> requested, BoqItem item) {
    List<BoqOperationDto> measures = requested.stream()
        .filter(d -> Boolean.TRUE.equals(d.isMeasure())).toList();
    if (measures.size() != 1) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_REQUIRED",
          "Weighted split needs exactly one measurement operation (got " + measures.size()
              + ") — its executed quantity becomes the line's billable quantity.");
    }
    String opUnit = measures.get(0).unit() == null ? "" : measures.get(0).unit().trim();
    String lineUnit = item.getUnit() == null ? "" : item.getUnit().trim();
    if (!opUnit.equalsIgnoreCase(lineUnit)) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_UNIT",
          "The measurement operation's unit ('" + opUnit + "') must equal the line's unit ('"
              + lineUnit + "') — it feeds billing directly.");
    }
    BigDecimal target = measures.get(0).targetQty();
    if (target == null || target.signum() <= 0) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_UNIT",
          "The measurement operation needs a positive target quantity (it cannot be a milestone).");
    }
    requireMeasureTargetEqualsQty(item, target);
  }

  /**
   * Owner rule (2026-08-05): the measurement operation IS the contracted deliverable — its target
   * must equal the line's boqQty, otherwise the line reports COMPLETED before (or never reaches
   * 100% after) the contracted quantity is billed. {@link BoqService} moves the target
   * automatically when boqQty is revised.
   */
  private void requireMeasureTargetEqualsQty(BoqItem item, BigDecimal target) {
    BigDecimal lineQty = item.getBoqQty();
    if (lineQty == null || lineQty.signum() <= 0) return;
    if (target.compareTo(lineQty) != 0) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_TARGET",
          "The measurement operation's target (" + target + ") must equal the line's contracted "
              + "quantity (" + lineQty + ") — it is the billable deliverable; a different target "
              + "would let % complete and billed quantity contradict each other.");
    }
  }

  /**
   * Owner rule (2026-08-05): partition children divide the line's contracted quantity, so the
   * plan must total it — every operation needs a positive target and Σ targets must equal
   * boqQty minus the frozen pre-split history ({@code opening} = the legacy operation's target).
   * Skipped when the line has no positive boqQty (nothing to reconcile against). A boqQty
   * revision (VO) deliberately leaves targets untouched — the next reweight re-enforces the sum.
   */
  private void validatePartitionTargets(BoqItem item, List<BigDecimal> targets, BigDecimal opening) {
    for (BigDecimal t : targets) {
      if (t == null || t.signum() <= 0) {
        throw new BusinessRuleException("BOQ_SPLIT_PARTITION_TARGET",
            "Every partition operation needs a positive target quantity — together they divide "
                + "the line's contracted quantity.");
      }
    }
    BigDecimal lineQty = item.getBoqQty();
    if (lineQty == null || lineQty.signum() <= 0) return;
    BigDecimal remaining = lineQty.subtract(nz(opening));
    if (remaining.signum() <= 0) {
      throw new BusinessRuleException("BOQ_SPLIT_PARTITION_TARGET",
          "Pre-split history (" + opening + ") already meets or exceeds the line quantity ("
              + lineQty + ") — revise the line quantity (VO) before partitioning.");
    }
    BigDecimal sum = targets.stream().map(BoqOperationService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (sum.subtract(remaining).abs().compareTo(QTY_TOLERANCE) > 0) {
      throw new BusinessRuleException("BOQ_SPLIT_PARTITION_TARGET",
          "Partition targets must sum to the line quantity"
              + (nz(opening).signum() > 0
                  ? " minus pre-split history (" + lineQty + " − " + opening + " = " + remaining + ")"
                  : " (" + remaining + ")")
              + " — got " + sum + ".");
    }
  }

  private void validateWeightsSum(List<BoqOperationDto> requested, BigDecimal legacyWeight,
                                  boolean hasHistory) {
    if (hasHistory && (legacyWeight == null || legacyWeight.signum() < 0)) {
      throw new BusinessRuleException("BOQ_SPLIT_LEGACY_WEIGHT",
          "This line already has executed quantity — set legacyWeight (the share of the line's "
              + "value the pre-split history represents).");
    }
    BigDecimal sum = requested.stream().map(d -> nz(d.weightPct())).reduce(BigDecimal.ZERO, BigDecimal::add)
        .add(nz(legacyWeight));
    if (sum.subtract(HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw new BusinessRuleException("BOQ_SPLIT_WEIGHTS_SUM",
          "Operation weights must sum to 100 (got " + sum + ").");
    }
  }

  /** Reweight's final-state twin of the split-time validations. */
  private void validateFinalState(BoqItem item, List<BoqOperation> ops) {
    List<BoqOperation> measures = ops.stream()
        .filter(op -> Boolean.TRUE.equals(op.getIsMeasure())).toList();
    if (measures.size() != 1) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_REQUIRED",
          "The line must keep exactly one measurement operation (got " + measures.size() + ").");
    }
    String opUnit = measures.get(0).getUnit() == null ? "" : measures.get(0).getUnit().trim();
    String lineUnit = item.getUnit() == null ? "" : item.getUnit().trim();
    if (!opUnit.equalsIgnoreCase(lineUnit)) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_UNIT",
          "The measurement operation's unit ('" + opUnit + "') must equal the line's unit ('"
              + lineUnit + "').");
    }
    // Review fix: the split-time "measurement op cannot be a milestone" rule must survive a
    // reweight — a null target makes the measure op binary, inflating the fraction to its full
    // weight on the first executed unit.
    BigDecimal target = measures.get(0).getTargetQty();
    if (target == null || target.signum() <= 0) {
      throw new BusinessRuleException("BOQ_SPLIT_MEASURE_UNIT",
          "The measurement operation needs a positive target quantity (it cannot be a milestone).");
    }
    requireMeasureTargetEqualsQty(item, target);
    BigDecimal sum = BigDecimal.ZERO;
    for (BoqOperation op : ops) {
      BigDecimal w = nz(op.getWeightPct());
      if (w.signum() < 0) {
        throw new BusinessRuleException("BOQ_SPLIT_WEIGHTS_SUM",
            "Operation weights cannot be negative (op '" + op.getOpCode() + "').");
      }
      sum = sum.add(w);
    }
    if (sum.subtract(HUNDRED).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw new BusinessRuleException("BOQ_SPLIT_WEIGHTS_SUM",
          "Operation weights must sum to 100 (got " + sum + ").");
    }
  }

  /**
   * L1: every activity linked to the line must be re-pointed to one of its operations in the same
   * transaction — a split line with an unassigned activity would immediately dead-end DPR entry
   * ({@code DPR_BOQ_OPERATION_REQUIRED}).
   */
  private void repointActivities(BoqItem item, List<BoqOperation> saved,
                                 Map<UUID, String> assignments) {
    Map<UUID, String> byActivity = assignments != null ? assignments : Map.of();
    @SuppressWarnings("unchecked")
    List<Object[]> linked = em.createNativeQuery(
            "SELECT id, name, planned_qty FROM activity.activities WHERE boq_item_id = :itemId")
        .setParameter("itemId", item.getId())
        .getResultList();

    Map<String, BoqOperation> byCode = new HashMap<>();
    saved.forEach(op -> byCode.put(op.getOpCode(), op));

    List<String> unassigned = new ArrayList<>();
    Map<UUID, Integer> activitiesPerOp = new HashMap<>();
    for (Object[] row : linked) {
      UUID activityId = (UUID) row[0];
      String code = byActivity.get(activityId);
      if (code == null || code.isBlank()) {
        unassigned.add(String.valueOf(row[1]));
        continue;
      }
      BoqOperation op = byCode.get(code.trim());
      if (op == null || Boolean.TRUE.equals(op.getIsLegacy())) {
        throw new BusinessRuleException("BOQ_SPLIT_ASSIGNMENT_OP_UNKNOWN",
            "Activity '" + row[1] + "' is assigned to operation '" + code + "' which "
                + (op == null ? "does not exist on this line." : "is the history operation — pick a real one."));
      }
      activitiesPerOp.merge(op.getId(), 1, Integer::sum);
    }
    if (!unassigned.isEmpty()) {
      throw new BusinessRuleException("BOQ_SPLIT_ACTIVITY_UNASSIGNED",
          "Every activity linked to " + item.getItemNo() + " must be assigned to an operation. "
              + "Missing: " + String.join(", ", unassigned));
    }
    for (Object[] row : linked) {
      UUID activityId = (UUID) row[0];
      BoqOperation op = byCode.get(byActivity.get(activityId).trim());
      em.createNativeQuery(
              "UPDATE activity.activities SET boq_operation_id = :opId WHERE id = :activityId")
          .setParameter("opId", op.getId())
          .setParameter("activityId", activityId)
          .executeUpdate();
      // §5.3 default carried over from Stage 3: a sole-covering activity's plannedQty defaults to
      // its operation's target so its own % has an honest denominator.
      boolean soleForOp = activitiesPerOp.getOrDefault(op.getId(), 0) == 1;
      if (soleForOp && row[2] == null && op.getTargetQty() != null) {
        em.createNativeQuery(
                "UPDATE activity.activities SET planned_qty = :target "
                    + "WHERE id = :activityId AND planned_qty IS NULL")
            .setParameter("target", op.getTargetQty())
            .setParameter("activityId", activityId)
            .executeUpdate();
      }
    }
  }

  /** D5: frozen once any DPR is attributed to an operation, or any approved DPR post-dates the split. */
  private boolean weightsFrozen(BoqItem item, List<BoqOperation> ops) {
    if (attributedDprCount(item.getId()) > 0) return true;
    LocalDate splitDate = ops.stream()
        .map(BoqOperation::getCreatedAt)
        .filter(java.util.Objects::nonNull)
        .min(java.util.Comparator.naturalOrder())
        .map(i -> i.atZone(ZoneId.systemDefault()).toLocalDate())
        .orElse(null);
    if (splitDate == null) return false;
    Number n = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM project.daily_progress_reports "
                + "WHERE boq_item_id = :itemId AND approval_status = 'APPROVED' "
                + "AND report_date > :splitDate")
        .setParameter("itemId", item.getId())
        .setParameter("splitDate", splitDate)
        .getSingleResult();
    return n.longValue() > 0;
  }

  private long attributedDprCount(UUID itemId) {
    Number n = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM project.daily_progress_reports d "
                + "JOIN project.boq_operations o ON o.id = d.boq_operation_id "
                + "WHERE o.boq_item_id = :itemId")
        .setParameter("itemId", itemId)
        .getSingleResult();
    return n.longValue();
  }

  /** Max quantity already billed to the employer for this line, or null when never billed. */
  private BigDecimal raBillCumulativeFloor(UUID itemId) {
    Object v = em.createNativeQuery(
            "SELECT MAX(cumulative_quantity) FROM cost.ra_bill_items WHERE boq_item_id = :itemId")
        .setParameter("itemId", itemId)
        .getSingleResult();
    return v == null ? null : new BigDecimal(v.toString());
  }

  /** Approved qty per operation id; pre-split rows (null operation) count toward the legacy op. */
  private Map<UUID, BigDecimal> executedByOperation(UUID projectId, UUID itemId,
                                                    List<BoqOperation> ops) {
    UUID legacyId = ops.stream()
        .filter(op -> Boolean.TRUE.equals(op.getIsLegacy()))
        .map(BoqOperation::getId)
        .findFirst().orElse(null);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery(
            "SELECT d.boq_operation_id, COALESCE(SUM(d.qty_executed), 0) "
                + "FROM project.daily_progress_reports d "
                + "WHERE d.project_id = :projectId AND d.boq_item_id = :itemId "
                + "AND d.approval_status = 'APPROVED' GROUP BY d.boq_operation_id")
        .setParameter("projectId", projectId)
        .setParameter("itemId", itemId)
        .getResultList();
    Map<UUID, BigDecimal> result = new HashMap<>();
    for (Object[] row : rows) {
      UUID opId = row[0] != null ? (UUID) row[0] : legacyId;
      if (opId == null) continue;   // pre-split rows on a line split without history op — ignore
      BigDecimal qty = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
      result.merge(opId, qty, BigDecimal::add);
    }
    return result;
  }

  private String describe(List<BoqOperation> ops) {
    StringBuilder sb = new StringBuilder();
    for (BoqOperation op : ops) {
      if (sb.length() > 0) sb.append("; ");
      sb.append(op.getOpCode()).append(" w").append(op.getWeightPct());
      if (Boolean.TRUE.equals(op.getIsMeasure())) sb.append(" MEASURE");
      if (Boolean.TRUE.equals(op.getIsLegacy())) sb.append(" LEGACY");
    }
    return sb.toString();
  }

  private BoqItem find(UUID projectId, UUID itemId) {
    BoqItem item = boqItemRepository.findById(itemId)
        .orElseThrow(() -> new ResourceNotFoundException("BoqItem", itemId));
    if (!item.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("BoqItem", itemId);
    }
    return item;
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
