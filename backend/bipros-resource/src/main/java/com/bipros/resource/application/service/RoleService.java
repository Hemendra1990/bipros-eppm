package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.CreateRoleRequest;
import com.bipros.resource.application.dto.RoleResponse;
import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
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
public class RoleService {

  private final ResourceRoleRepository roleRepository;

  public RoleResponse createRole(CreateRoleRequest request) {
    log.info("Creating role: code={}, type={}", request.code(), request.resourceType());

    if (roleRepository.findByCode(request.code()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_ROLE_CODE", "Role with code " + request.code() + " already exists");
    }

    Role role = Role.builder()
        .code(request.code())
        .name(request.name())
        .description(request.description())
        .resourceType(request.resourceType())
        .defaultRate(request.defaultRate())
        .sortOrder(0)
        .build();

    Role saved = roleRepository.save(role);
    log.info("Role created: id={}", saved.getId());
    return RoleResponse.from(saved);
  }

  public RoleResponse getRole(UUID id) {
    log.info("Fetching role: id={}", id);
    Role role = roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    return RoleResponse.from(role);
  }

  public List<RoleResponse> listRoles() {
    log.info("Listing all roles");
    return roleRepository.findAll().stream()
        .map(RoleResponse::from)
        .toList();
  }

  public List<RoleResponse> listRolesByResourceType(ResourceType resourceType) {
    log.info("Listing roles by resource type: {}", resourceType);
    return roleRepository.findByResourceType(resourceType).stream()
        .map(RoleResponse::from)
        .toList();
  }

  public RoleResponse updateRole(UUID id, CreateRoleRequest request) {
    log.info("Updating role: id={}", id);
    Role role = roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role", id));

    if (!role.getCode().equals(request.code()) &&
        roleRepository.findByCode(request.code()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_ROLE_CODE", "Role with code " + request.code() + " already exists");
    }

    role.setCode(request.code());
    role.setName(request.name());
    role.setDescription(request.description());
    role.setResourceType(request.resourceType());
    role.setDefaultRate(request.defaultRate());

    Role updated = roleRepository.save(role);
    log.info("Role updated: id={}", id);
    return RoleResponse.from(updated);
  }

  public void deleteRole(UUID id) {
    log.info("Deleting role: id={}", id);
    if (!roleRepository.existsById(id)) {
      throw new ResourceNotFoundException("Role", id);
    }
    roleRepository.deleteById(id);
    log.info("Role deleted: id={}", id);
  }
}
