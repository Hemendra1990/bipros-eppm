package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.BoqItemResponse;
import com.bipros.project.application.dto.BoqSummaryResponse;
import com.bipros.project.application.dto.CreateBoqItemRequest;
import com.bipros.project.application.dto.UpdateBoqItemRequest;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BoqService {

  private static final int RATIO_SCALE = 6;

  private final BoqItemRepository boqItemRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

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
        .build();
    BoqCalculator.recompute(item);
    BoqItem saved = boqItemRepository.save(item);
    auditService.logCreate("BoqItem", saved.getId(), BoqItemResponse.from(saved));
    return BoqItemResponse.from(saved);
  }

  public List<BoqItemResponse> createItemsBulk(UUID projectId, List<CreateBoqItemRequest> requests) {
    ensureProjectExists(projectId);
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
              .build();
          BoqCalculator.recompute(item);
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
    if (request.wbsNodeId() != null) item.setWbsNodeId(request.wbsNodeId());
    if (request.boqQty() != null) item.setBoqQty(request.boqQty());
    if (request.boqRate() != null) item.setBoqRate(request.boqRate());
    if (request.budgetedRate() != null) item.setBudgetedRate(request.budgetedRate());
    if (request.qtyExecutedToDate() != null) item.setQtyExecutedToDate(request.qtyExecutedToDate());
    if (request.actualRate() != null) item.setActualRate(request.actualRate());
    BoqCalculator.recompute(item);
    BoqItem saved = boqItemRepository.save(item);
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
      boqItemRepository.save(item);
    });
  }

  public void deleteItem(UUID projectId, UUID itemId) {
    BoqItem item = find(projectId, itemId);
    boqItemRepository.delete(item);
    auditService.logDelete("BoqItem", itemId);
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

    for (BoqItem i : items) {
      boqTotal = boqTotal.add(nz(i.getBoqAmount()));
      budgetedTotal = budgetedTotal.add(nz(i.getBudgetedAmount()));
      actualTotal = actualTotal.add(nz(i.getActualAmount()));
      BigDecimal earned = nz(i.getQtyExecutedToDate()).multiply(nz(i.getBudgetedRate()));
      earnedBudgetTotal = earnedBudgetTotal.add(earned);
    }

    BigDecimal grandVariance = actualTotal.subtract(earnedBudgetTotal).setScale(2, RoundingMode.HALF_UP);
    BigDecimal grandVariancePct = earnedBudgetTotal.signum() == 0
        ? null
        : grandVariance.divide(earnedBudgetTotal, RATIO_SCALE, RoundingMode.HALF_UP);
    BigDecimal overallPct = budgetedTotal.signum() == 0
        ? null
        : earnedBudgetTotal.divide(budgetedTotal, RATIO_SCALE, RoundingMode.HALF_UP);

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
}
