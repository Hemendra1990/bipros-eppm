package com.bipros.admin.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.admin.application.dto.AdminCategoryDto;
import com.bipros.admin.application.dto.CreateAdminCategoryRequest;
import com.bipros.admin.domain.model.AdminCategory;
import com.bipros.admin.domain.repository.AdminCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCategoryService {

    private final AdminCategoryRepository adminCategoryRepository;
    private final AuditService auditService;

    public AdminCategoryDto createCategory(CreateAdminCategoryRequest request) {
        AdminCategory category = new AdminCategory();
        category.setCategoryType(request.getCategoryType());
        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setSortOrder(request.getSortOrder());

        AdminCategory saved = adminCategoryRepository.save(category);
        auditService.logCreate("AdminCategory", saved.getId(), mapToDto(saved));
        return mapToDto(saved);
    }

    public AdminCategoryDto updateCategory(UUID id, CreateAdminCategoryRequest request) {
        AdminCategory category = adminCategoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdminCategory", id));

        category.setCategoryType(request.getCategoryType());
        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setSortOrder(request.getSortOrder());

        AdminCategory updated = adminCategoryRepository.save(category);
        auditService.logUpdate("AdminCategory", id, "category", null, mapToDto(updated));
        return mapToDto(updated);
    }

    public void deleteCategory(UUID id) {
        AdminCategory category = adminCategoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdminCategory", id));

        adminCategoryRepository.deleteAll(adminCategoryRepository.findByParentId(id));
        adminCategoryRepository.delete(category);
        auditService.logDelete("AdminCategory", id);
    }

    @Transactional(readOnly = true)
    public AdminCategoryDto getCategory(UUID id) {
        AdminCategory category = adminCategoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AdminCategory", id));
        return mapToDto(category);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryDto> listCategories(String categoryType) {
        List<AdminCategory> categories;
        if (categoryType != null) {
            categories = adminCategoryRepository.findByCategoryType(categoryType);
        } else {
            categories = adminCategoryRepository.findAll();
        }
        return categories.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryDto> getChildCategories(UUID parentId) {
        return adminCategoryRepository.findByParentId(parentId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    private AdminCategoryDto mapToDto(AdminCategory category) {
        return AdminCategoryDto.builder()
            .id(category.getId())
            .categoryType(category.getCategoryType())
            .code(category.getCode())
            .name(category.getName())
            .parentId(category.getParentId())
            .sortOrder(category.getSortOrder())
            .build();
    }
}
