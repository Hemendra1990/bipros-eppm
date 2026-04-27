package com.bipros.risk.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.risk.application.dto.CreateRiskCategoryMasterRequest;
import com.bipros.risk.application.dto.RiskCategoryMasterResponse;
import com.bipros.risk.application.dto.RiskCategorySummaryDto;
import com.bipros.risk.application.dto.RiskCategoryTypeSummary;
import com.bipros.risk.application.dto.UpdateRiskCategoryMasterRequest;
import com.bipros.risk.domain.model.Industry;
import com.bipros.risk.domain.model.RiskCategoryMaster;
import com.bipros.risk.domain.model.RiskCategoryType;
import com.bipros.risk.domain.repository.RiskCategoryMasterRepository;
import com.bipros.risk.domain.repository.RiskCategoryTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RiskCategoryMasterService {

    private final RiskCategoryMasterRepository repository;
    private final RiskCategoryTypeRepository typeRepository;

    public RiskCategoryMasterResponse create(CreateRiskCategoryMasterRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new BusinessRuleException(
                "RISK_CATEGORY_DUPLICATE",
                "Risk category with code '" + code + "' already exists");
        }
        RiskCategoryType type = typeRepository.findById(request.typeId())
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryType", request.typeId()));
        RiskCategoryMaster entity = RiskCategoryMaster.builder()
            .code(code)
            .name(request.name().trim())
            .description(request.description())
            .type(type)
            .industry(request.industry())
            .active(request.active())
            .sortOrder(request.sortOrder())
            .systemDefault(Boolean.FALSE)
            .build();
        RiskCategoryMaster saved = repository.save(entity);
        log.info("Created risk category: {} (type={}, industry={})",
            saved.getCode(), type.getCode(), saved.getIndustry());
        return toResponse(saved);
    }

    public RiskCategoryMasterResponse update(UUID id, UpdateRiskCategoryMasterRequest request) {
        RiskCategoryMaster entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryMaster", id));
        RiskCategoryType type = typeRepository.findById(request.typeId())
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryType", request.typeId()));
        // Code and systemDefault are immutable after creation.
        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        entity.setType(type);
        entity.setIndustry(request.industry());
        entity.setActive(request.active());
        entity.setSortOrder(request.sortOrder());
        RiskCategoryMaster saved = repository.save(entity);
        log.info("Updated risk category: {}", saved.getCode());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        RiskCategoryMaster entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryMaster", id));
        if (Boolean.TRUE.equals(entity.getSystemDefault())) {
            throw new BusinessRuleException(
                "SYSTEM_DEFAULT_PROTECTED",
                "System-default risk categories cannot be deleted; deactivate instead");
        }
        // FK from risks/risk_templates protects against deleting in-use rows; the DB will
        // throw DataIntegrityViolationException which the global handler converts to 409.
        repository.delete(entity);
        log.info("Deleted risk category: {}", id);
    }

    @Transactional(readOnly = true)
    public RiskCategoryMasterResponse getById(UUID id) {
        return repository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("RiskCategoryMaster", id));
    }

    @Transactional(readOnly = true)
    public Optional<RiskCategoryMaster> findEntityByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return repository.findByCode(normalizeCode(code));
    }

    @Transactional(readOnly = true)
    public Optional<RiskCategoryMasterResponse> findByCode(String code) {
        return findEntityByCode(code).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryMasterResponse> listAll() {
        return repository.findAll().stream()
            .sorted(Comparator
                .comparingInt((RiskCategoryMaster c) -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                .thenComparing(RiskCategoryMaster::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryMasterResponse> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryMasterResponse> listByTypeId(UUID typeId) {
        return repository.findByTypeIdAndActiveTrueOrderBySortOrderAsc(typeId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RiskCategoryMasterResponse> listByTypeAndIndustry(UUID typeId, Industry industry) {
        Set<Industry> industries = industry == null
            ? EnumSet.allOf(Industry.class)
            : EnumSet.of(industry, Industry.GENERIC);
        if (typeId != null) {
            return repository.findByTypeIdAndIndustryInAndActiveTrueOrderBySortOrderAsc(typeId, industries)
                .stream().map(this::toResponse).collect(Collectors.toList());
        }
        return repository.findByIndustryInAndActiveTrueOrderBySortOrderAsc(industries).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    /** Build the embedded summary used in Risk and RiskTemplate responses. */
    public static RiskCategorySummaryDto toSummary(RiskCategoryMaster entity) {
        if (entity == null) return null;
        RiskCategoryType type = entity.getType();
        RiskCategoryTypeSummary typeSummary = type == null
            ? null
            : new RiskCategoryTypeSummary(type.getId(), type.getCode(), type.getName());
        return new RiskCategorySummaryDto(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getIndustry(),
            typeSummary);
    }

    private static String normalizeCode(String raw) {
        return raw.trim().toUpperCase().replace(' ', '_');
    }

    private RiskCategoryMasterResponse toResponse(RiskCategoryMaster entity) {
        RiskCategoryType type = entity.getType();
        RiskCategoryTypeSummary typeSummary = type == null
            ? null
            : new RiskCategoryTypeSummary(type.getId(), type.getCode(), type.getName());
        return new RiskCategoryMasterResponse(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            entity.getIndustry(),
            typeSummary,
            entity.getActive(),
            entity.getSortOrder(),
            entity.getSystemDefault());
    }
}
