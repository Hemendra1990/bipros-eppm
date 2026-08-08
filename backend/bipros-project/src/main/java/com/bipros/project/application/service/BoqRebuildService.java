package com.bipros.project.application.service;

import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Rebuilds every BOQ item's qtyExecutedToDate/earnedFraction + actualRate from DPRs, from
 *  scratch (idempotent). */
@Service
@RequiredArgsConstructor
@Slf4j
public class BoqRebuildService {

  private final BoqItemRepository boqRepo;
  private final BoqService boqService;
  private final BoqActualCostQuery boqActualCostQuery;

  @Transactional
  public int rebuildFromDprs(UUID projectId) {
    int rebuilt = 0;
    for (BoqItem item : boqRepo.findByProjectId(projectId)) {
      UUID boqId = item.getId();
      // Stage 4 (A10): the qty/earned-fraction rebuild delegates to the canonical split-aware
      // roll-up — the pre-Stage-4 flat DPR sum would have zeroed every split line's derived
      // numbers in one idempotent-looking transaction.
      boqService.recomputeExecutedQtyApproved(projectId, boqId);
      // A10 manualOverride reconciliation: it protects the manually-set actualRate ONLY —
      // quantities/fraction were still rebuilt above (previously the whole row was skipped,
      // freezing stale quantities forever).
      if (!Boolean.TRUE.equals(item.getManualOverride())) {
        BigDecimal qty = nz(item.getQtyExecutedToDate());   // measured basis (A5)
        BigDecimal cost = boqActualCostQuery.sumActualCost(projectId, boqId);
        BigDecimal actualRate = qty.signum() == 0
            ? BigDecimal.ZERO
            : cost.divide(qty, 4, RoundingMode.HALF_UP);

        item.setActualRate(actualRate);
        BoqCalculator.recompute(item);          // reuse canonical derived-field math
        // Review fix: the old local qty-only status mirror contradicted the fraction-aware
        // canonical logic on split lines — share the one method instead of mirroring it.
        BoqService.applyAutoStatus(item);
        boqRepo.save(item);
      }
      rebuilt++;
    }
    log.info("[BoqRebuildService] project {} rebuilt {} BOQ items from DPRs", projectId, rebuilt);
    return rebuilt;
  }

  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
