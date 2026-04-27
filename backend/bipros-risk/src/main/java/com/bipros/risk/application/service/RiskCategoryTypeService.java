package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.CreateRiskCategoryTypeRequest;
import com.bipros.risk.application.dto.RiskCategoryTypeResponse;
import com.bipros.risk.application.dto.UpdateRiskCategoryTypeRequest;
import com.bipros.risk.domain.model.RiskCategoryType;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskCategoryTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RiskCategoryTypeService {

    private final RiskCategoryTypeRepository typeRepository;
    private final RiskCategoryMasterRepository categoryRepository;

    public RiskCategoryTypeResponse create(CreateRiskCategoryTypeRequest request) {
        String code = normalizeCode(request.code());
        if (typeRepository.existsByCode(code)) {
            throw new BusinessRuleException(
                "RISK_CATEGORY_TYPE_DUPLICATE",
                "Risk category type with code '" + code + "' already exists");
        }
        RiskCategoryType entity = RiskCategoryType.builder()
            .code(code)
            .name(request.name().trim())
            .description(request.description())
            .active(request.active())
            .sortOrder(request.sortOrder())
            .systemDefault(Boolean.FALSE)
            .build();
        RiskCategoryType saved = typeRepository.save(entity);
        log.info("Created risk category type: {}", saved.getCode());
        return toResponse(saved);
    }

    public RiskCategoryTypeResponse update(UUID id, UpdateRiskCategoryTypeRequest request) {
        RiskCategoryType entity = typeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryType", id));
        // Code and systemDefault are immutable after creation.
        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        entity.setSortOrder(request.sortOrder());
        RiskCategoryType saved = typeRepository.save(entity);
        log.info("Updated risk category type: {}", saved.getCode());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        RiskCategoryType entity = typeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryType", id));
        if (Boolean.TRUE.equals(entity.getSystemDefault())) {
            throw new BusinessRuleException(
                "SYSTEM_DEFAULT_PROTECTED",
                "System-default risk category types cannot be deleted; deactivate instead");
        }
        long children = categoryRepository.countByTypeId(id);
        if (children > 0) {
            throw new BusinessRuleException(
                "TYPE_HAS_CHILDREN",
                "Cannot delete a risk category type with " + children + " categories under it");
        }
        typeRepository.delete(entity);
        log.info("Deleted risk category type: {}", id);
    }

    @Transactional(readOnly = true)
    public RiskCategoryTypeResponse getById(UUID id) {
        return typeRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryType", id));
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryTypeResponse> listAll() {
        return typeRepository.findAll().stream()
            .sorted(Comparator
                .comparingInt((RiskCategoryType t) -> t.getSortOrder() == null ? 0 : t.getSortOrder())
                .thenComparing(RiskCategoryType::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryTypeResponse> listActive() {
        return typeRepository.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private static String normalizeCode(String raw) {
        return raw.trim().toUpperCase().replace(' ', '_');
    }

    private RiskCategoryTypeResponse toResponse(RiskCategoryType entity) {
        return new RiskCategoryTypeResponse(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            entity.getActive(),
            entity.getSortOrder(),
            entity.getSystemDefault(),
            categoryRepository.countByTypeId(entity.getId()));
    }
}
