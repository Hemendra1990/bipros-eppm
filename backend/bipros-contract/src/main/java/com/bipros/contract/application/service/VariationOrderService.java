package com.bipros.contract.application.service;

import com.bipros.common.event.VariationOrderApprovedEvent;
import com.bipros.common.event.VoLineItemAction;
import com.bipros.common.event.VoLineItemPayload;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.VariationOrderRequest;
import com.bipros.contract.application.dto.VariationOrderResponse;
import com.bipros.contract.application.dto.VoLineItemRequest;
import com.bipros.contract.application.dto.VoLineItemResponse;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.Contract;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.model.VariationOrderStatus;
import com.bipros.contract.domain.model.VoLineItem;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.ContractRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import com.bipros.contract.domain.repository.VoLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class VariationOrderService {

    private final VariationOrderRepository variationOrderRepository;
    private final VoLineItemRepository voLineItemRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final ContractAttachmentService attachmentService;
    private final ContractRepository contractRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public VariationOrderResponse create(VariationOrderRequest request) {
        log.info("Creating variation order for contract: {}", request.contractId());

        VariationOrder vo = new VariationOrder();
        vo.setContractId(request.contractId());
        vo.setVoNumber(request.voNumber());
        vo.setDescription(request.description());
        vo.setVoValue(request.voValue());
        vo.setJustification(request.justification());
        vo.setImpactOnBudget(request.impactOnBudget());
        vo.setImpactOnScheduleDays(request.impactOnScheduleDays());
        vo.setApprovedBy(request.approvedBy());
        vo.setStatus(VariationOrderStatus.INITIATED);

        VariationOrder saved = variationOrderRepository.save(vo);
        log.info("Variation order created with ID: {}", saved.getId());

        List<VoLineItem> lineItems = persistLineItems(saved.getId(), request.lineItems());
        VariationOrderResponse response = toResponse(saved, 0L, lineItems);
        auditService.logCreate("VariationOrder", saved.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public VariationOrderResponse getById(UUID id) {
        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            vo.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        List<VoLineItem> lineItems = voLineItemRepository.findByVariationOrderId(id);
        return toResponse(vo, count, lineItems);
    }

    @Transactional(readOnly = true)
    public List<VariationOrderResponse> listByContract(UUID contractId) {
        List<VariationOrder> rows = variationOrderRepository.findByContractId(contractId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Long> counts = attachmentService.countsByEntities(
            contractId, AttachmentEntityType.VARIATION_ORDER,
            rows.stream().map(VariationOrder::getId).toList());
        return rows.stream()
            .map(vo -> toResponse(
                vo,
                counts.getOrDefault(vo.getId(), 0L),
                voLineItemRepository.findByVariationOrderId(vo.getId())))
            .toList();
    }

    public VariationOrderResponse update(UUID id, VariationOrderRequest request) {
        log.info("Updating variation order with ID: {}", id);

        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));

        // Disallow header changes once the VO has been approved — the BOQ side is already mutated.
        if (vo.getStatus() == VariationOrderStatus.APPROVED) {
            throw new BusinessRuleException("VO_LOCKED",
                "Approved VOs are read-only — create a new VO to amend the contract further.");
        }

        vo.setDescription(request.description());
        vo.setVoValue(request.voValue());
        vo.setJustification(request.justification());
        vo.setImpactOnBudget(request.impactOnBudget());
        vo.setImpactOnScheduleDays(request.impactOnScheduleDays());
        vo.setApprovedBy(request.approvedBy());

        VariationOrder updated = variationOrderRepository.save(vo);

        // Replace line items wholesale — UI sends the desired final set on each save.
        voLineItemRepository.deleteByVariationOrderId(id);
        List<VoLineItem> lineItems = persistLineItems(updated.getId(), request.lineItems());

        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            updated.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        VariationOrderResponse response = toResponse(updated, count, lineItems);
        auditService.logUpdate("VariationOrder", id, "vo", null, response);
        return response;
    }

    /**
     * Transition the VO to a new status. Approving a VO publishes
     * {@link VariationOrderApprovedEvent} carrying the structured line items so
     * {@code VoApprovedBoqMutationListener} (in {@code bipros-project}) can update the
     * matching {@code BoqItem} rows transactionally.
     */
    public VariationOrderResponse updateStatus(UUID id, VariationOrderStatus newStatus, String approvedBy) {
        log.info("Transitioning VO {} to status {}", id, newStatus);
        if (newStatus == null) {
            throw new BusinessRuleException("VO_STATUS_REQUIRED", "status is required");
        }
        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));

        VariationOrderStatus previous = vo.getStatus();
        if (previous == newStatus) {
            long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
                vo.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
            return toResponse(vo, count, voLineItemRepository.findByVariationOrderId(id));
        }

        vo.setStatus(newStatus);
        if (newStatus == VariationOrderStatus.APPROVED) {
            vo.setApprovedAt(Instant.now());
            if (approvedBy != null) vo.setApprovedBy(approvedBy);
        }
        VariationOrder saved = variationOrderRepository.save(vo);
        auditService.logUpdate("VariationOrder", id, "status", previous, newStatus);

        List<VoLineItem> lineItems = voLineItemRepository.findByVariationOrderId(id);
        if (newStatus == VariationOrderStatus.APPROVED) {
            // Look up the parent project so listeners outside the contract module don't have to.
            Contract contract = contractRepository.findById(saved.getContractId())
                .orElseThrow(() -> new ResourceNotFoundException("Contract", saved.getContractId()));
            List<VoLineItemPayload> payloads = lineItems.stream()
                .map(li -> new VoLineItemPayload(
                    li.getId(),
                    li.getAction(),
                    li.getBoqItemId(),
                    li.getNewItemNo(),
                    li.getNewItemDescription(),
                    li.getNewItemUnit(),
                    li.getRevisedQty(),
                    li.getRevisedRate(),
                    li.getLineImpactAmount()))
                .toList();
            eventPublisher.publishEvent(new VariationOrderApprovedEvent(
                saved.getId(),
                saved.getContractId(),
                contract.getProjectId(),
                saved.getVoNumber(),
                saved.getVoValue(),
                saved.getImpactOnBudget(),
                saved.getImpactOnScheduleDays(),
                payloads
            ));
        }

        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            saved.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        return toResponse(saved, count, lineItems);
    }

    public void delete(UUID id) {
        log.info("Deleting variation order with ID: {}", id);
        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));
        if (vo.getStatus() == VariationOrderStatus.APPROVED) {
            throw new BusinessRuleException("VO_LOCKED",
                "Approved VOs cannot be deleted — they have already mutated the BOQ. Create a reversing VO if needed.");
        }
        attachmentService.deleteAllForEntity(
            vo.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        voLineItemRepository.deleteByVariationOrderId(id);
        variationOrderRepository.deleteById(id);
        auditService.logDelete("VariationOrder", id);
    }

    /**
     * Wholesale replacement of the line items for a VO. Caller has already cleared existing
     * rows for the VO. Returns the freshly-persisted list in submission order.
     */
    private List<VoLineItem> persistLineItems(UUID voId, List<VoLineItemRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<VoLineItem> entities = requests.stream()
            .peek(VariationOrderService::assertLineItemConsistency)
            .map(r -> VoLineItem.builder()
                .variationOrderId(voId)
                .action(r.action())
                .boqItemId(r.boqItemId())
                .newItemNo(r.newItemNo())
                .newItemDescription(r.newItemDescription())
                .newItemUnit(r.newItemUnit())
                .revisedQty(r.revisedQty())
                .revisedRate(r.revisedRate())
                .lineImpactAmount(r.lineImpactAmount())
                .build())
            .toList();
        return voLineItemRepository.saveAll(entities);
    }

    private static void assertLineItemConsistency(VoLineItemRequest r) {
        if (r.action() == null) {
            throw new BusinessRuleException("VO_LINE_ACTION_REQUIRED", "Line item action is required");
        }
        switch (r.action()) {
            case ADD_ITEM -> {
                if (r.newItemNo() == null || r.newItemNo().isBlank()) {
                    throw new BusinessRuleException("VO_LINE_NEW_ITEM_NO_REQUIRED",
                        "ADD_ITEM line items must specify newItemNo");
                }
                if (r.revisedQty() == null || r.revisedRate() == null) {
                    throw new BusinessRuleException("VO_LINE_QTY_RATE_REQUIRED",
                        "ADD_ITEM line items must specify revisedQty and revisedRate");
                }
            }
            case REVISE_QTY -> {
                if (r.boqItemId() == null) {
                    throw new BusinessRuleException("VO_LINE_BOQ_ITEM_REQUIRED",
                        "REVISE_QTY line items must reference an existing BoqItem");
                }
                if (r.revisedQty() == null) {
                    throw new BusinessRuleException("VO_LINE_REVISED_QTY_REQUIRED",
                        "REVISE_QTY line items must specify revisedQty");
                }
            }
            case REVISE_RATE -> {
                if (r.boqItemId() == null) {
                    throw new BusinessRuleException("VO_LINE_BOQ_ITEM_REQUIRED",
                        "REVISE_RATE line items must reference an existing BoqItem");
                }
                if (r.revisedRate() == null) {
                    throw new BusinessRuleException("VO_LINE_REVISED_RATE_REQUIRED",
                        "REVISE_RATE line items must specify revisedRate");
                }
            }
            case DELETE_ITEM -> {
                if (r.boqItemId() == null) {
                    throw new BusinessRuleException("VO_LINE_BOQ_ITEM_REQUIRED",
                        "DELETE_ITEM line items must reference an existing BoqItem");
                }
            }
        }
        // Avoid an unused-variable warning if the enum grows: this is intentional fallthrough-checking
        if (r.action() == VoLineItemAction.ADD_ITEM && r.boqItemId() != null) {
            throw new BusinessRuleException("VO_LINE_NEW_ITEM_FK",
                "ADD_ITEM line items must not reference an existing BoqItem (boqItemId must be null)");
        }
    }

    private VariationOrderResponse toResponse(VariationOrder vo, long attachmentCount, List<VoLineItem> lineItems) {
        return new VariationOrderResponse(
            vo.getId(),
            vo.getContractId(),
            vo.getVoNumber(),
            vo.getDescription(),
            vo.getVoValue(),
            vo.getJustification(),
            vo.getStatus(),
            vo.getImpactOnBudget(),
            vo.getImpactOnScheduleDays(),
            vo.getApprovedBy(),
            vo.getApprovedAt(),
            attachmentCount,
            vo.getCreatedAt(),
            vo.getUpdatedAt(),
            lineItems == null ? List.of() : lineItems.stream().map(VoLineItemResponse::from).toList()
        );
    }
}
