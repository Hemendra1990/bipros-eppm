package com.bipros.contract.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.contract.application.dto.VariationOrderRequest;
import com.bipros.contract.application.dto.VariationOrderResponse;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.contract.domain.model.VariationOrderStatus;
import com.bipros.contract.domain.repository.VariationOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class VariationOrderService {

    private final VariationOrderRepository variationOrderRepository;

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

        return toResponse(saved);
    }

    public VariationOrderResponse getById(UUID id) {
        log.info("Fetching variation order with ID: {}", id);

        VariationOrder vo = variationOrderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("VariationOrder", id));

        return toResponse(vo);
    }

    public List<VariationOrderResponse> listByContract(UUID contractId) {
        log.info("Listing variation orders for contract: {}", contractId);

        return variationOrderRepository.findByContractId(contractId).stream()
            .map(this::toResponse)
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
        return toResponse(updated);
    }

    public void delete(UUID id) {
        log.info("Deleting variation order with ID: {}", id);
        variationOrderRepository.deleteById(id);
    }

    private VariationOrderResponse toResponse(VariationOrder vo) {
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
            vo.getCreatedAt(),
            vo.getUpdatedAt()
        );
    }
}
