package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.AddToPoolRequest;
import com.bipros.resource.application.dto.ProjectResourceResponse;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.application.dto.UpdatePoolEntryRequest;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ProjectResource;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ProjectResourceService {

    private final ProjectResourceRepository projectResourceRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceAssignmentRepository assignmentRepository;
    private final AuditService auditService;

    public List<ProjectResourceResponse> listPool(UUID projectId) {
        log.info("Listing resource pool for project: projectId={}", projectId);
        List<ProjectResource> poolEntries = projectResourceRepository.findByProjectId(projectId);
        return hydratePool(poolEntries);
    }

    public List<ResourceResponse> listAvailable(UUID projectId, String typeCode, UUID roleId, String q) {
        log.info("Listing available resources for pool: projectId={}, typeCode={}, roleId={}, q={}",
                projectId, typeCode, roleId, q);

        List<ProjectResource> existing = projectResourceRepository.findByProjectId(projectId);
        Map<UUID, ProjectResource> pooledMap = existing.stream()
                .collect(Collectors.toMap(ProjectResource::getResourceId, pr -> pr));

        List<Resource> allResources = resourceRepository.findAll();
        return allResources.stream()
                .filter(r -> r.getStatus() == com.bipros.resource.domain.model.ResourceStatus.ACTIVE)
                .filter(r -> !pooledMap.containsKey(r.getId()))
                .filter(r -> typeCode == null || (r.getResourceType() != null && typeCode.equals(r.getResourceType().getCode())))
                .filter(r -> roleId == null || (r.getRole() != null && roleId.equals(r.getRole().getId())))
                .filter(r -> q == null || q.isBlank()
                        || r.getName().toLowerCase().contains(q.toLowerCase())
                        || r.getCode().toLowerCase().contains(q.toLowerCase()))
                .map(ResourceResponse::from)
                .toList();
    }

    public List<ProjectResourceResponse> listPoolByRole(UUID projectId, UUID roleId) {
        log.info("Listing pool entries by role: projectId={}, roleId={}", projectId, roleId);
        List<ProjectResource> poolEntries = projectResourceRepository.findByProjectId(projectId);
        List<UUID> pooledResourceIds = poolEntries.stream()
                .map(ProjectResource::getResourceId)
                .toList();

        if (pooledResourceIds.isEmpty()) return List.of();

        Map<UUID, Resource> resourceMap = resourceRepository.findAllById(pooledResourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, r -> r));

        return poolEntries.stream()
                .filter(pr -> {
                    Resource r = resourceMap.get(pr.getResourceId());
                    return r != null && r.getRole() != null && roleId.equals(r.getRole().getId());
                })
                .map(pr -> {
                    Resource r = resourceMap.get(pr.getResourceId());
                    return toResponse(pr, r);
                })
                .toList();
    }

    public List<ProjectResourceResponse> addToPool(UUID projectId, AddToPoolRequest request) {
        log.info("Adding {} resources to pool for project: projectId={}", request.entries().size(), projectId);

        List<ProjectResourceResponse> results = new ArrayList<>();
        for (var entry : request.entries()) {
            if (projectResourceRepository.existsByProjectIdAndResourceId(projectId, entry.resourceId())) {
                log.debug("Resource already in pool, skipping: resourceId={}", entry.resourceId());
                continue;
            }

            if (!resourceRepository.existsById(entry.resourceId())) {
                throw new ResourceNotFoundException("Resource", entry.resourceId());
            }

            ProjectResource pr = ProjectResource.builder()
                    .projectId(projectId)
                    .resourceId(entry.resourceId())
                    .rateOverride(entry.rateOverride())
                    .availabilityOverride(entry.availabilityOverride())
                    .customUnit(entry.customUnit())
                    .notes(entry.notes())
                    .build();

            ProjectResource saved = projectResourceRepository.save(pr);
            Resource resource = resourceRepository.findById(entry.resourceId()).orElse(null);
            results.add(toResponse(saved, resource));
            auditService.logCreate("ProjectResource", saved.getId(), results.get(results.size() - 1));
        }

        log.info("Added {} resources to pool for project: projectId={}", results.size(), projectId);
        return results;
    }

    public ProjectResourceResponse updatePoolEntry(UUID projectId, UUID id, UpdatePoolEntryRequest request) {
        log.info("Updating pool entry: projectId={}, id={}", projectId, id);

        ProjectResource pr = projectResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectResource", id));

        if (!pr.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("POOL_ENTRY_PROJECT_MISMATCH",
                    "Pool entry does not belong to the specified project");
        }

        if (request.rateOverride() != null) pr.setRateOverride(request.rateOverride());
        if (request.availabilityOverride() != null) pr.setAvailabilityOverride(request.availabilityOverride());
        if (request.customUnit() != null) pr.setCustomUnit(request.customUnit());
        if (request.notes() != null) pr.setNotes(request.notes());

        ProjectResource saved = projectResourceRepository.save(pr);
        Resource resource = resourceRepository.findById(pr.getResourceId()).orElse(null);
        ProjectResourceResponse response = toResponse(saved, resource);
        auditService.logUpdate("ProjectResource", saved.getId(), "pool-entry", pr, response);
        return response;
    }

    public void removeFromPool(UUID projectId, UUID id) {
        log.info("Removing from pool: projectId={}, id={}", projectId, id);

        ProjectResource pr = projectResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectResource", id));

        if (!pr.getProjectId().equals(projectId)) {
            throw new BusinessRuleException("POOL_ENTRY_PROJECT_MISMATCH",
                    "Pool entry does not belong to the specified project");
        }

        List<ResourceAssignment> blocking = assignmentRepository.findByResourceId(pr.getResourceId()).stream()
                .filter(a -> a.getProjectId().equals(projectId))
                .toList();

        if (!blocking.isEmpty()) {
            String activityIds = blocking.stream()
                    .map(a -> a.getActivityId().toString())
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleException("POOL_ENTRY_HAS_ASSIGNMENTS",
                    "Cannot remove resource from pool: it has active assignments in activities: " + activityIds);
        }

        projectResourceRepository.delete(pr);
        auditService.logDelete("ProjectResource", id);
    }

    public BigDecimal resolveRateOverride(UUID projectId, UUID resourceId) {
        return projectResourceRepository.findByProjectIdAndResourceId(projectId, resourceId)
                .map(ProjectResource::getRateOverride)
                .orElse(null);
    }

    public boolean isInPool(UUID projectId, UUID resourceId) {
        return projectResourceRepository.existsByProjectIdAndResourceId(projectId, resourceId);
    }

    private List<ProjectResourceResponse> hydratePool(List<ProjectResource> poolEntries) {
        if (poolEntries.isEmpty()) return List.of();

        List<UUID> resourceIds = poolEntries.stream()
                .map(ProjectResource::getResourceId)
                .distinct()
                .toList();

        Map<UUID, Resource> resourceMap = resourceRepository.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, r -> r));

        return poolEntries.stream()
                .map(pr -> toResponse(pr, resourceMap.get(pr.getResourceId())))
                .toList();
    }

    private ProjectResourceResponse toResponse(ProjectResource pr, Resource resource) {
        String resourceCode = resource != null ? resource.getCode() : null;
        String resourceName = resource != null ? resource.getName() : null;
        String resourceTypeName = resource != null && resource.getResourceType() != null
                ? resource.getResourceType().getName() : null;
        String roleName = resource != null && resource.getRole() != null
                ? resource.getRole().getName() : null;
        BigDecimal masterRate = resource != null ? resource.getCostPerUnit() : null;

        return ProjectResourceResponse.from(pr, resourceCode, resourceName, resourceTypeName, roleName, masterRate);
    }
}
