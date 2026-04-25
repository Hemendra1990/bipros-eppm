package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.contract.application.dto.VariationOrderRequest;
import com.bipros.contract.application.dto.VariationOrderResponse;
import com.bipros.contract.domain.model.AttachmentEntityType;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.model.VariationOrderStatus;
import com.bipros.contract.domain.repository.ContractAttachmentRepository;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class VariationOrderService {

    private final VariationOrderRepository variationOrderRepository;
    private final ContractAttachmentRepository attachmentRepository;
    private final ContractAttachmentService attachmentService;
    private final AuditService auditService;

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
        auditService.logCreate("VariationOrder", saved.getId(), toResponse(saved, 0L));

        return toResponse(saved, 0L);
    }

    @Transactional(readOnly = true)
    public VariationOrderResponse getById(UUID id) {
        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            vo.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        return toResponse(vo, count);
    }

    @Transactional(readOnly = true)
    public List<VariationOrderResponse> listByContract(UUID contractId) {
        List<VariationOrder> rows = variationOrderRepository.findByContractId(contractId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, Long> counts = attachmentService.countsByEntities(
            contractId, AttachmentEntityType.VARIATION_ORDER,
            rows.stream().map(VariationOrder::getId).toList());
        return rows.stream()
            .map(vo -> toResponse(vo, counts.getOrDefault(vo.getId(), 0L)))
            .toList();
    }

    public VariationOrderResponse update(UUID id, VariationOrderRequest request) {
        log.info("Updating variation order with ID: {}", id);

        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));

        vo.setDescription(request.description());
        vo.setVoValue(request.voValue());
        vo.setJustification(request.justification());
        vo.setImpactOnBudget(request.impactOnBudget());
        vo.setImpactOnScheduleDays(request.impactOnScheduleDays());
        vo.setApprovedBy(request.approvedBy());

        VariationOrder updated = variationOrderRepository.save(vo);
        long count = attachmentRepository.countByContractIdAndEntityTypeAndEntityId(
            updated.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        VariationOrderResponse response = toResponse(updated, count);
        auditService.logUpdate("VariationOrder", id, "vo", null, response);
        return response;
    }

    public void delete(UUID id) {
        log.info("Deleting variation order with ID: {}", id);
        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));
        attachmentService.deleteAllForEntity(
            vo.getContractId(), AttachmentEntityType.VARIATION_ORDER, id);
        variationOrderRepository.deleteById(id);
        auditService.logDelete("VariationOrder", id);
    }

    private VariationOrderResponse toResponse(VariationOrder vo, long attachmentCount) {
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
            vo.getUpdatedAt()
        );
    }
}
