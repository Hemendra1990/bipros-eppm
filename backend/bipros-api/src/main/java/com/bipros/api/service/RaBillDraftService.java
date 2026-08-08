package com.bipros.api.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.cost.application.dto.RaBillDto;
import com.bipros.cost.application.dto.RaBillItemDto;
import com.bipros.cost.application.service.RaBillDraftCalculator;
import com.bipros.cost.application.service.RaBillDraftCalculator.BoqLineSnapshot;
import com.bipros.cost.application.service.RaBillDraftCalculator.DeductionConfig;
import com.bipros.cost.application.service.RaBillDraftCalculator.DraftResult;
import com.bipros.cost.application.service.RaBillDraftCalculator.RaBillItemDraft;
import com.bipros.cost.domain.entity.RaBill;
import com.bipros.cost.domain.entity.RaBill.RaBillStatus;
import com.bipros.cost.domain.entity.RaBillItem;
import com.bipros.cost.domain.repository.RaBillItemRepository;
import com.bipros.cost.domain.repository.RaBillRepository;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates RA Bill draft generation across modules — reads BOQ from {@code bipros-project},
 * reads previous bills from {@code bipros-cost}, runs the math via {@link RaBillDraftCalculator}.
 *
 * <p>Lives in {@code bipros-api} because it needs both modules' repositories and {@code
 * bipros-cost} on its own does not depend on {@code bipros-project}. This mirrors the
 * Phase 1.3 / {@code ActivityCostSummaryController} pattern: stateless math in the cost
 * module, cross-module orchestration in the aggregator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RaBillDraftService {

  private static final List<RaBillStatus> CERTIFIED_STATUSES = List.of(
      RaBillStatus.CERTIFIED, RaBillStatus.APPROVED, RaBillStatus.PAID,
      RaBillStatus.PAID_PMC_OVERRIDE);

  private final BoqItemRepository boqItemRepository;
  private final RaBillRepository raBillRepository;
  private final RaBillItemRepository raBillItemRepository;
  private final ContractRepository contractRepository;
  private final AuditService auditService;

  public record DraftPreview(
      RaBillDto bill,
      List<RaBillItemDto> items,
      UUID resolvedContractId,
      int contractCount,
      List<UUID> projectContractIds
  ) {}

  /**
   * Build a draft RA bill for the given project + period. {@code save=false} returns a
   * preview without persisting. {@code save=true} commits the draft as a {@link
   * RaBillStatus#DRAFT} bill plus its line items in a single transaction.
   *
   * @param contractId optional explicit contract scope. If null and the project has exactly
   *                   one contract, that contract is auto-selected. If null and the project
   *                   has multiple contracts, a {@link BusinessRuleException} is thrown.
   */
  @Transactional
  public DraftPreview generateDraft(
      UUID projectId, LocalDate from, LocalDate to, UUID contractId, boolean save) {

    UUID resolvedContractId = resolveContractId(projectId, contractId);

    // Latest certified bill for this contract — its cumulative quantities define the floor.
    RaBill previousBill = raBillRepository
        .findByProjectIdOrderByBillNumberDesc(projectId).stream()
        .filter(b -> resolvedContractId.equals(b.getContractId()))
        .filter(b -> CERTIFIED_STATUSES.contains(b.getStatus()))
        .findFirst()
        .orElse(null);

    Map<UUID, Double> previousCumulative = new HashMap<>();
    BigDecimal previousCumulativeAmount = BigDecimal.ZERO;
    if (previousBill != null) {
      List<RaBillItem> prev = raBillItemRepository.findByRaBillIdOrderByCreatedAt(previousBill.getId());
      for (RaBillItem it : prev) {
        if (it.getBoqItemId() != null && it.getCumulativeQuantity() != null) {
          previousCumulative.merge(it.getBoqItemId(), it.getCumulativeQuantity(), Math::max);
        }
      }
      if (previousBill.getCumulativeAmount() != null) {
        previousCumulativeAmount = previousBill.getCumulativeAmount();
      }
    }

    // Project BOQ snapshot in stable order (by item_no, natural).
    List<BoqItem> boq = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);
    List<BoqLineSnapshot> snapshots = boq.stream()
        .map(b -> new BoqLineSnapshot(
            b.getId(),
            b.getItemNo(),
            b.getDescription(),
            b.getUnit(),
            b.getBoqRate(),
            b.getQtyExecutedToDate(),
            b.getBoqQty()))
        .toList();

    DeductionConfig deductions = DeductionConfig.defaults();
    DraftResult result = RaBillDraftCalculator.compute(snapshots, previousCumulative, deductions);

    if (!save) {
      RaBill previewEntity = buildBillEntity(
          projectId, resolvedContractId, from, to, result, previousCumulativeAmount, null);
      List<RaBillItem> previewItems = result.items().stream()
          .map(d -> toItemEntity(d, null))
          .toList();
      return assemblePreview(previewEntity, previewItems, projectId, resolvedContractId);
    }

    // Save path — persist a DRAFT bill with its line items.
    String billNumber = nextBillNumber(projectId);
    RaBill bill = buildBillEntity(
        projectId, resolvedContractId, from, to, result, previousCumulativeAmount, billNumber);
    RaBill savedBill = raBillRepository.save(bill);
    auditService.logCreate("RaBill", savedBill.getId(), RaBillDto.from(savedBill));

    List<RaBillItem> persisted = new ArrayList<>(result.items().size());
    for (RaBillItemDraft d : result.items()) {
      RaBillItem item = toItemEntity(d, savedBill.getId());
      persisted.add(raBillItemRepository.save(item));
    }
    return assemblePreview(savedBill, persisted, projectId, resolvedContractId);
  }

  private RaBill buildBillEntity(
      UUID projectId, UUID contractId, LocalDate from, LocalDate to,
      DraftResult result, BigDecimal previousCumulativeAmount, String billNumber) {
    RaBill bill = new RaBill();
    bill.setProjectId(projectId);
    bill.setContractId(contractId);
    bill.setBillNumber(billNumber != null ? billNumber : "DRAFT-PREVIEW");
    bill.setBillPeriodFrom(from);
    bill.setBillPeriodTo(to);
    bill.setGrossAmount(result.grossAmount());
    bill.setDeductions(result.totalDeductions());
    bill.setMobAdvanceRecovery(result.mobAdvanceRecovery());
    bill.setRetention5Pct(result.retention5Pct());
    bill.setTds2Pct(result.tds2Pct());
    bill.setGst18Pct(result.gst18Pct());
    bill.setNetAmount(result.netAmount());
    bill.setCumulativeAmount(previousCumulativeAmount.add(result.netAmount()));
    bill.setStatus(RaBillStatus.DRAFT);
    return bill;
  }

  private RaBillItem toItemEntity(RaBillItemDraft d, UUID raBillId) {
    RaBillItem item = new RaBillItem();
    if (raBillId != null) item.setRaBillId(raBillId);
    item.setBoqItemId(d.boqItemId());
    item.setItemCode(d.itemCode());
    item.setDescription(d.description() != null ? d.description() : d.itemCode());
    item.setUnit(d.unit());
    item.setRate(d.rate());
    item.setPreviousQuantity(d.previousQuantity());
    item.setCurrentQuantity(d.currentQuantity());
    item.setCumulativeQuantity(d.cumulativeQuantity());
    item.setAmount(d.amount());
    return item;
  }

  private DraftPreview assemblePreview(
      RaBill bill, List<RaBillItem> items, UUID projectId, UUID resolvedContractId) {
    List<UUID> projectContractIds = contractRepository.findByProjectId(projectId).stream()
        .map(Contract::getId)
        .toList();
    return new DraftPreview(
        RaBillDto.from(bill),
        items.stream().map(RaBillItemDto::from).toList(),
        resolvedContractId,
        projectContractIds.size(),
        projectContractIds);
  }

  private UUID resolveContractId(UUID projectId, UUID requested) {
    List<Contract> projectContracts = contractRepository.findByProjectId(projectId);
    if (projectContracts.isEmpty()) {
      throw new BusinessRuleException(
          "RA_BILL_NO_CONTRACT",
          "Project " + projectId + " has no contracts — cannot generate an RA Bill draft.");
    }
    if (requested != null) {
      boolean belongs = projectContracts.stream().anyMatch(c -> c.getId().equals(requested));
      if (!belongs) {
        throw new BusinessRuleException(
            "RA_BILL_CONTRACT_MISMATCH",
            "Contract " + requested + " does not belong to project " + projectId + ".");
      }
      return requested;
    }
    if (projectContracts.size() > 1) {
      throw new BusinessRuleException(
          "RA_BILL_CONTRACT_REQUIRED",
          "Project has " + projectContracts.size() + " contracts — pass contractId explicitly.");
    }
    return projectContracts.get(0).getId();
  }

  /**
   * Next bill number for a project. Pattern: {@code RA-{projectShort}-{NN}} where NN is the
   * 1-based ordinal across this project's bills (regardless of status). Loose pattern
   * because the bill_number column is just unique-by-string; the planner can override
   * later via the existing PUT endpoint if a different scheme is needed.
   */
  private String nextBillNumber(UUID projectId) {
    long count = raBillRepository.findByProjectIdOrderByBillNumberDesc(projectId).size();
    String shortId = projectId.toString().substring(0, 8);
    return String.format("RA-%s-%03d", shortId, count + 1);
  }

  /**
   * Convenience for callers that need to enumerate the project's contracts without going
   * through the contract module directly. Used by the controller to surface the contract
   * list when the auto-default fails.
   */
  public List<Contract> projectContractsSorted(UUID projectId) {
    return contractRepository.findByProjectId(projectId).stream()
        .sorted(Comparator.comparing(Contract::getContractNumber, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }
}
