package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.CreateMaterialIssueRequest;
import com.bipros.resource.application.dto.CreateMaterialReturnRequest;
import com.bipros.resource.application.dto.MaterialIssueResponse;
import com.bipros.resource.application.dto.MaterialReturnResponse;
import com.bipros.resource.application.service.MaterialIssueService;
import com.bipros.resource.application.service.MaterialReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MaterialIssueController {

    private final MaterialIssueService service;
    private final MaterialReturnService returnService;

    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'STORE.READ')")
    @GetMapping("/projects/{projectId}/issues")
    public ResponseEntity<ApiResponse<List<MaterialIssueResponse>>> listByProject(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listByProject(projectId)));
    }

    @PostMapping("/projects/{projectId}/issues")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'STORE.UPDATE')")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMaterialIssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(service.create(projectId, request)));
    }

    @GetMapping("/issues/{id}")
    @PreAuthorize("hasPermission(null, 'STORE.READ')")
    public ResponseEntity<ApiResponse<MaterialIssueResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @GetMapping("/materials/{materialId}/issues")
    @PreAuthorize("hasPermission(null, 'STORE.READ')")
    public ResponseEntity<ApiResponse<List<MaterialIssueResponse>>> listByMaterial(
            @PathVariable UUID materialId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listByMaterial(materialId)));
    }

    /** Record material coming back against the slip that issued it. */
    @PostMapping("/projects/{projectId}/issues/{issueId}/returns")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'STORE.UPDATE')")
    public ResponseEntity<ApiResponse<MaterialReturnResponse>> createReturn(
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @Valid @RequestBody CreateMaterialReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(returnService.create(projectId, issueId, request)));
    }

    @GetMapping("/projects/{projectId}/returns")
    @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'STORE.READ')")
    public ResponseEntity<ApiResponse<List<MaterialReturnResponse>>> listReturns(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(returnService.listByProject(projectId)));
    }
}
