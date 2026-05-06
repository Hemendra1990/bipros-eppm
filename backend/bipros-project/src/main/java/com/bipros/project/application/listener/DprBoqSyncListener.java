package com.bipros.project.application.listener;

import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.project.application.service.BoqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Keeps {@code BoqItem.qtyExecutedToDate} in step with DPR mutations. Listens within the same
 * transaction as the DPR write (no {@code @TransactionalEventListener} — synchronous, so a BOQ
 * recompute failure rolls the DPR write back). For UPDATED, applies a delta if the BOQ item
 * link is unchanged, or rebalances both old and new items if the link was redirected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DprBoqSyncListener {

  private final BoqService boqService;

  @EventListener
  public void onDprSubmitted(DprSubmittedEvent event) {
    DprMutationType type = event.eventType();
    if (type == null) {
      // Defensive: legacy callers shouldn't exist, but treat missing type as a no-op.
      return;
    }
    switch (type) {
      case CREATED -> applyCreate(event);
      case UPDATED -> applyUpdate(event);
      case DELETED -> applyDelete(event);
    }
  }

  private void applyCreate(DprSubmittedEvent event) {
    if (isBlank(event.boqItemNo()) || event.qtyExecuted() == null) return;
    boqService.addExecutedQty(event.projectId(), event.boqItemNo(), event.qtyExecuted());
  }

  private void applyUpdate(DprSubmittedEvent event) {
    String oldItem = event.oldBoqItemNo();
    String newItem = event.boqItemNo();
    BigDecimal oldQty = nz(event.oldQty());
    BigDecimal newQty = nz(event.qtyExecuted());

    if (Objects.equals(blankToNull(oldItem), blankToNull(newItem))) {
      // Same item (or both null): apply the delta.
      if (!isBlank(newItem)) {
        BigDecimal delta = newQty.subtract(oldQty);
        if (delta.signum() > 0) {
          boqService.addExecutedQty(event.projectId(), newItem, delta);
        } else if (delta.signum() < 0) {
          boqService.subtractExecutedQty(event.projectId(), newItem, delta.abs());
        }
      }
      return;
    }

    // Item link changed: undo the old, apply the new.
    if (!isBlank(oldItem) && oldQty.signum() > 0) {
      boqService.subtractExecutedQty(event.projectId(), oldItem, oldQty);
    }
    if (!isBlank(newItem) && newQty.signum() > 0) {
      boqService.addExecutedQty(event.projectId(), newItem, newQty);
    }
  }

  private void applyDelete(DprSubmittedEvent event) {
    if (isBlank(event.oldBoqItemNo()) || event.oldQty() == null) return;
    boqService.subtractExecutedQty(event.projectId(), event.oldBoqItemNo(), event.oldQty());
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return isBlank(s) ? null : s;
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }
}
