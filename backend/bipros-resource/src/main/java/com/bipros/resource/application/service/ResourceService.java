package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.CreateResourceRequest;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceRepository;
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
public class ResourceService {

  private final ResourceRepository resourceRepository;

  public ResourceResponse createResource(CreateResourceRequest request) {
    log.info("Creating resource: code={}, type={}", request.code(), request.resourceType());

    if (resourceRepository.findByCode(request.code()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE", "Resource with code " + request.code() + " already exists");
    }

    Resource resource = Resource.builder()
        .code(request.code())
        .name(request.name())
        .resourceType(request.resourceType())
        .parentId(request.parentId())
        .calendarId(request.calendarId())
        .email(request.email())
        .phone(request.phone())
        .title(request.title())
        .maxUnitsPerDay(request.maxUnitsPerDay() != null ? request.maxUnitsPerDay() : 8.0)
        .status(ResourceStatus.ACTIVE)
        .sortOrder(0)
        .build();

    Resource saved = resourceRepository.save(resource);
    log.info("Resource created: id={}", saved.getId());
    return ResourceResponse.from(saved);
  }

  public ResourceResponse getResource(UUID id) {
    log.info("Fetching resource: id={}", id);
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    return ResourceResponse.from(resource);
  }

  public List<ResourceResponse> listResources() {
    log.info("Listing all resources");
    return resourceRepository.findAll().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByType(ResourceType type) {
    log.info("Listing resources by type: {}", type);
    return resourceRepository.findByResourceType(type).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourceHierarchyRoots() {
    log.info("Listing resource hierarchy roots");
    return resourceRepository.findByParentIdIsNull().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByParent(UUID parentId) {
    log.info("Listing resources by parent: {}", parentId);
    return resourceRepository.findByParentId(parentId).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByStatus(ResourceStatus status) {
    log.info("Listing resources by status: {}", status);
    return resourceRepository.findByStatus(status).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public ResourceResponse updateResource(UUID id, CreateResourceRequest request) {
    log.info("Updating resource: id={}", id);
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));

    if (!resource.getCode().equals(request.code()) &&
        resourceRepository.findByCode(request.code()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE", "Resource with code " + request.code() + " already exists");
    }

    resource.setCode(request.code());
    resource.setName(request.name());
    resource.setResourceType(request.resourceType());
    resource.setParentId(request.parentId());
    resource.setCalendarId(request.calendarId());
    resource.setEmail(request.email());
    resource.setPhone(request.phone());
    resource.setTitle(request.title());
    if (request.maxUnitsPerDay() != null) {
      resource.setMaxUnitsPerDay(request.maxUnitsPerDay());
    }

    Resource updated = resourceRepository.save(resource);
    log.info("Resource updated: id={}", id);
    return ResourceResponse.from(updated);
  }

  public void deleteResource(UUID id) {
    log.info("Deleting resource: id={}", id);
    if (!resourceRepository.existsById(id)) {
      throw new ResourceNotFoundException("Resource", id);
    }
    resourceRepository.deleteById(id);
    log.info("Resource deleted: id={}", id);
  }
}
