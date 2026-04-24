package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateGoodsReceiptRequest;
import com.bipros.resource.application.dto.GoodsReceiptResponse;
import com.bipros.resource.domain.model.GoodsReceiptNote;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.repository.GoodsReceiptNoteRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class GoodsReceiptService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final GoodsReceiptNoteRepository grnRepository;
    private final MaterialRepository materialRepository;
    private final MaterialStockService stockService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> listByProject(UUID projectId) {
        return grnRepository.findByProjectIdOrderByReceivedDateDesc(projectId).stream()
            .map(GoodsReceiptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> listByMaterial(UUID materialId) {
        return grnRepository.findByMaterialIdOrderByReceivedDateDesc(materialId).stream()
            .map(GoodsReceiptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse get(UUID id) {
        return GoodsReceiptResponse.from(grnRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GoodsReceiptNote", id)));
    }

    public GoodsReceiptResponse create(UUID projectId, CreateGoodsReceiptRequest request) {
        Material material = materialRepository.findById(request.materialId())
            .orElseThrow(() -> new ResourceNotFoundException("Material", request.materialId()));
        if (!material.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("MATERIAL_PROJECT_MISMATCH",
                "Material " + material.getCode() + " belongs to a different project");
        }

        BigDecimal amount = null;
        if (request.quantity() != null && request.unitRate() != null) {
            amount = request.quantity().multiply(request.unitRate()).setScale(2, RoundingMode.HALF_UP);
        }

        GoodsReceiptNote grn = GoodsReceiptNote.builder()
            .projectId(projectId)
            .grnNumber(generateGrnNumber(request.receivedDate()))
            .materialId(request.materialId())
            .receivedDate(request.receivedDate())
            .quantity(request.quantity())
            .unitRate(request.unitRate())
            .amount(amount)
            .supplierOrganisationId(request.supplierOrganisationId())
            .poNumber(request.poNumber())
            .vehicleNumber(request.vehicleNumber())
            .receivedByUserId(request.receivedByUserId())
            .acceptedQuantity(request.acceptedQuantity() != null ? request.acceptedQuantity() : request.quantity())
            .rejectedQuantity(request.rejectedQuantity() != null ? request.rejectedQuantity() : BigDecimal.ZERO)
            .remarks(request.remarks())
            .build();

        GoodsReceiptNote saved = grnRepository.save(grn);
        stockService.recordGrn(saved);
        auditService.logCreate("GoodsReceiptNote", saved.getId(), GoodsReceiptResponse.from(saved));
        return GoodsReceiptResponse.from(saved);
    }

    private String generateGrnNumber(LocalDate date) {
        String month = (date != null ? date : LocalDate.now()).format(MONTH_FORMAT);
        String like = "GRN-" + month + "-%";
        int suffixStart = "GRN-".length() + month.length() + 2; // 1-based PostgreSQL substring
        Integer max = grnRepository.findMaxSuffixForPrefix(like, suffixStart);
        int next = max == null ? 1 : max + 1;
        return String.format("GRN-%s-%04d", month, next);
    }
}
