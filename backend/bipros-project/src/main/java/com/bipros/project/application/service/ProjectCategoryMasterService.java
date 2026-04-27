package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateProjectCategoryMasterRequest;
import com.bipros.project.application.dto.ProjectCategoryMasterResponse;
import com.bipros.project.application.dto.UpdateProjectCategoryMasterRequest;
import com.bipros.project.domain.model.ProjectCategoryMaster;
import com.bipros.project.domain.repository.ProjectCategoryMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProjectCategoryMasterService {

    private final ProjectCategoryMasterRepository repository;

    public ProjectCategoryMasterResponse create(CreateProjectCategoryMasterRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new BusinessRuleException(
                "CATEGORY_CODE_DUPLICATE",
                "Project category with code '" + request.code() + "' already exists");
        }

        ProjectCategoryMaster entity = new ProjectCategoryMaster();
        entity.setCode(request.code().trim().toUpperCase().replace(' ', '_'));
        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        entity.setSortOrder(request.sortOrder());

        ProjectCategoryMaster saved = repository.save(entity);
        log.info("Created project category master: {}", saved.getCode());
        return toResponse(saved);
    }

    public ProjectCategoryMasterResponse update(UUID id, UpdateProjectCategoryMasterRequest request) {
        ProjectCategoryMaster entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectCategoryMaster", id));

        entity.setName(request.name().trim());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        entity.setSortOrder(request.sortOrder());

        ProjectCategoryMaster saved = repository.save(entity);
        log.info("Updated project category master: {}", saved.getCode());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        ProjectCategoryMaster entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectCategoryMaster", id));
        repository.delete(entity);
        log.info("Deleted project category master: {}", id);
    }

    @Transactional(readOnly = true)
    public ProjectCategoryMasterResponse getById(UUID id) {
        return repository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectCategoryMaster", id));
    }

    @Transactional(readOnly = true)
    public List<ProjectCategoryMasterResponse> listAll() {
        return repository.findAll().stream()
            .sorted((a, b) -> {
                int cmp = Integer.compare(a.getSortOrder(), b.getSortOrder());
                return cmp != 0 ? cmp : a.getName().compareToIgnoreCase(b.getName());
            })
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectCategoryMasterResponse> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAsc().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private ProjectCategoryMasterResponse toResponse(ProjectCategoryMaster entity) {
        return new ProjectCategoryMasterResponse(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            entity.getActive(),
            entity.getSortOrder()
        );
    }
}
