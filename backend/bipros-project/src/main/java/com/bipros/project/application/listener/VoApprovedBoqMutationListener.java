package com.bipros.project.application.listener;

import com.bipros.common.event.VariationOrderApprovedEvent;
import com.bipros.project.application.service.BoqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Applies the structured BOQ mutations (add / revise / delete) carried on a
 * {@link VariationOrderApprovedEvent} when a VO is approved.
 *
 * <p>Runs at {@link TransactionPhase#BEFORE_COMMIT} so a failure here rolls back the VO
 * approval transaction — without this we'd end up with an APPROVED VO whose BOQ effects
 * silently failed, which would silently overstate billable progress. Legacy header-only VOs
 * (empty {@code lineItems}) no-op cleanly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoApprovedBoqMutationListener {

  private final BoqService boqService;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onVariationOrderApproved(VariationOrderApprovedEvent event) {
    if (event.lineItems() == null || event.lineItems().isEmpty()) {
      return;
    }
    List<UUID> impacted = boqService.applyVoLineItems(event.projectId(), event.lineItems());
    log.info("VO {} approved: applied {} line items affecting BoQ rows {}",
        event.voNumber(), event.lineItems().size(), impacted);
  }
}
