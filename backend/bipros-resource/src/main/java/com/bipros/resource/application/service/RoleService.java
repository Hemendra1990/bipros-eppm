package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.AssignUserToRoleRequest;
import com.bipros.resource.application.dto.CreateRoleRequest;
import com.bipros.resource.application.dto.RoleResponse;
import com.bipros.resource.application.dto.UserResourceRoleResponse;
import com.bipros.resource.domain.model.Role;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.UserResourceRole;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.UserResourceRoleRepository;
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
  private final UserResourceRoleRepository userResourceRoleRepository;
  private final AuditService auditService;

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
        .rateUnit(request.rateUnit())
        .budgetedRate(request.budgetedRate())
        .actualRate(request.actualRate())
        .rateRemarks(request.rateRemarks())
        .sortOrder(0)
        .build();

    Role saved = roleRepository.save(role);
    log.info("Role created: id={}", saved.getId());

    // Audit log creation
    auditService.logCreate("Role", saved.getId(), RoleResponse.from(saved));

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
    role.setRateUnit(request.rateUnit());
    role.setBudgetedRate(request.budgetedRate());
    role.setActualRate(request.actualRate());
    role.setRateRemarks(request.rateRemarks());

    Role updated = roleRepository.save(role);
    log.info("Role updated: id={}", id);

    // Audit log update
    auditService.logUpdate("Role", id, "role", role, RoleResponse.from(updated));

    return RoleResponse.from(updated);
  }

  public void deleteRole(UUID id) {
    log.info("Deleting role: id={}", id);
    if (!roleRepository.existsById(id)) {
      throw new ResourceNotFoundException("Role", id);
    }
    roleRepository.deleteById(id);
    log.info("Role deleted: id={}", id);

    // Audit log deletion
    auditService.logDelete("Role", id);
  }

  // ───────────────────── User ↔ ResourceRole assignment ─────────────────────

  public UserResourceRoleResponse assignUser(UUID roleId, AssignUserToRoleRequest request) {
    if (!roleRepository.existsById(roleId)) {
      throw new ResourceNotFoundException("Role", roleId);
    }
    userResourceRoleRepository.findByUserIdAndResourceRoleId(request.userId(), roleId)
        .ifPresent(existing -> {
          throw new BusinessRuleException("USER_ROLE_ALREADY_ASSIGNED",
              "User " + request.userId() + " is already assigned to role " + roleId);
        });
    UserResourceRole urr = UserResourceRole.builder()
        .userId(request.userId())
        .resourceRoleId(roleId)
        .assignedFrom(request.assignedFrom())
        .assignedTo(request.assignedTo())
        .remarks(request.remarks())
        .build();
    UserResourceRole saved = userResourceRoleRepository.save(urr);
    auditService.logCreate("UserResourceRole", saved.getId(), UserResourceRoleResponse.from(saved));
    return UserResourceRoleResponse.from(saved);
  }

  public void unassignUser(UUID roleId, UUID assignmentId) {
    UserResourceRole urr = userResourceRoleRepository.findById(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("UserResourceRole", assignmentId));
    if (!urr.getResourceRoleId().equals(roleId)) {
      throw new ResourceNotFoundException("UserResourceRole", assignmentId);
    }
    userResourceRoleRepository.delete(urr);
    auditService.logDelete("UserResourceRole", assignmentId);
  }

  @Transactional(readOnly = true)
  public List<UserResourceRoleResponse> listUsersForRole(UUID roleId) {
    if (!roleRepository.existsById(roleId)) {
      throw new ResourceNotFoundException("Role", roleId);
    }
    return userResourceRoleRepository.findByResourceRoleId(roleId).stream()
        .map(UserResourceRoleResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserResourceRoleResponse> listRolesForUser(UUID userId) {
    return userResourceRoleRepository.findByUserId(userId).stream()
        .map(UserResourceRoleResponse::from)
        .toList();
  }
}
