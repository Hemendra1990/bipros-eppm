package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateRoleRequest;
import com.bipros.resource.application.dto.RoleResponse;
import com.bipros.resource.application.service.RoleService;
import com.bipros.resource.domain.model.ResourceType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

  private final RoleService roleService;

  @PostMapping
  public ResponseEntity<ApiResponse<RoleResponse>> createRole(
      @Valid @RequestBody CreateRoleRequest request) {
    log.info("POST /v1/roles - Creating role: {}", request.code());
    RoleResponse response = roleService.createRole(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable UUID id) {
    log.info("GET /v1/roles/{} - Fetching role", id);
    RoleResponse response = roleService.getRole(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles(
      @RequestParam(required = false) ResourceType resourceType) {
    log.info("GET /v1/roles - Listing roles, resourceType={}", resourceType);
    List<RoleResponse> response;
    if (resourceType != null) {
      response = roleService.listRolesByResourceType(resourceType);
    } else {
      response = roleService.listRoles();
    }
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
      @PathVariable UUID id,
      @Valid @RequestBody CreateRoleRequest request) {
    log.info("PUT /v1/roles/{} - Updating role", id);
    RoleResponse response = roleService.updateRole(id, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
    log.info("DELETE /v1/roles/{} - Deleting role", id);
    roleService.deleteRole(id);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
