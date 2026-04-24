package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateMaterialRequest;
import com.bipros.resource.application.dto.MaterialResponse;
import com.bipros.resource.domain.model.Material;
import com.bipros.resource.domain.model.MaterialBoqLink;
import com.bipros.resource.domain.model.MaterialCategory;
import com.bipros.resource.domain.model.MaterialStatus;
import com.bipros.resource.domain.repository.MaterialBoqLinkRepository;
import com.bipros.resource.domain.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialBoqLinkRepository boqLinkRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MaterialResponse> listByProject(UUID projectId, MaterialCategory category) {
        List<Material> rows = category != null
            ? materialRepository.findByProjectIdAndCategory(projectId, category)
            : materialRepository.findByProjectId(projectId);
        return rows.stream().map(this::hydrate).toList();
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(UUID id) {
        return hydrate(findOrThrow(id));
    }

    public MaterialResponse create(UUID projectId, CreateMaterialRequest request) {
        String code = request.code() != null && !request.code().isBlank()
            ? request.code() : generateCode(projectId);
        if (materialRepository.existsByProjectIdAndCode(projectId, code)) {
            throw new BusinessRuleException("DUPLICATE_MATERIAL_CODE",
                "Material code '" + code + "' already exists for this project");
        }

        Material m = Material.builder()
            .projectId(projectId)
            .code(code)
            .name(request.name())
            .category(request.category())
            .unit(request.unit())
            .specificationGrade(request.specificationGrade())
            .minStockLevel(request.minStockLevel())
            .reorderQuantity(request.reorderQuantity())
            .leadTimeDays(request.leadTimeDays())
            .storageLocation(request.storageLocation())
            .approvedSupplierId(request.approvedSupplierId())
            .status(request.status() != null ? request.status() : MaterialStatus.ACTIVE)
            .build();
        Material saved = materialRepository.save(m);
        replaceBoqLinks(saved.getId(), request.applicableBoqItemIds());
        auditService.logCreate("Material", saved.getId(), hydrate(saved));
        return hydrate(saved);
    }

    public MaterialResponse update(UUID id, CreateMaterialRequest request) {
        Material m = findOrThrow(id);
        if (request.name() != null) m.setName(request.name());
        if (request.category() != null) m.setCategory(request.category());
        if (request.unit() != null) m.setUnit(request.unit());
        if (request.specificationGrade() != null) m.setSpecificationGrade(request.specificationGrade());
        if (request.minStockLevel() != null) m.setMinStockLevel(request.minStockLevel());
        if (request.reorderQuantity() != null) m.setReorderQuantity(request.reorderQuantity());
        if (request.leadTimeDays() != null) m.setLeadTimeDays(request.leadTimeDays());
        if (request.storageLocation() != null) m.setStorageLocation(request.storageLocation());
        if (request.approvedSupplierId() != null) m.setApprovedSupplierId(request.approvedSupplierId());
        if (request.status() != null) m.setStatus(request.status());

        Material saved = materialRepository.save(m);
        if (request.applicableBoqItemIds() != null) {
            replaceBoqLinks(saved.getId(), request.applicableBoqItemIds());
        }
        auditService.logUpdate("Material", id, "material", null, hydrate(saved));
        return hydrate(saved);
    }

    public void delete(UUID id) {
        Material m = findOrThrow(id);
        boqLinkRepository.deleteByMaterialId(id);
        materialRepository.delete(m);
        auditService.logDelete("Material", id);
    }

    private void replaceBoqLinks(UUID materialId, List<UUID> boqItemIds) {
        if (boqItemIds == null) return;
        boqLinkRepository.deleteByMaterialId(materialId);
        boqLinkRepository.flush();
        for (UUID id : boqItemIds) {
            MaterialBoqLink link = new MaterialBoqLink();
            link.setMaterialId(materialId);
            link.setBoqItemId(id);
            boqLinkRepository.save(link);
        }
    }

    private MaterialResponse hydrate(Material m) {
        List<UUID> ids = boqLinkRepository.findByMaterialId(m.getId()).stream()
            .map(MaterialBoqLink::getBoqItemId)
            .toList();
        return MaterialResponse.from(m, ids);
    }

    private Material findOrThrow(UUID id) {
        return materialRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Material", id));
    }

    private String generateCode(UUID projectId) {
        Integer max = materialRepository.findMaxSuffix(projectId);
        int next = max == null ? 1 : max + 1;
        return String.format("MAT-%03d", next);
    }
}
