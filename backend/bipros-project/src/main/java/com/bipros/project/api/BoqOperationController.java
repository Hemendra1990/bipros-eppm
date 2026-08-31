package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.BoqOperationDto;
import com.bipros.project.application.dto.SplitBoqItemRequest;
import com.bipros.project.application.service.BoqOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Split lifecycle of a BOQ line (Stage 4) — operations of {@code /boq/{itemId}}. */
@RestController
@RequestMapping("/v1/projects/{projectId}/boq/{itemId}/operations")
@RequiredArgsConstructor
@Slf4j
public class BoqOperationController {

  private final BoqOperationService boqOperationService;

  @GetMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT.READ')")
  public ResponseEntity<ApiResponse<List<BoqOperationDto>>> list(
      @PathVariable UUID projectId,
      @PathVariable UUID itemId) {
    return ResponseEntity.ok(ApiResponse.ok(boqOperationService.list(projectId, itemId)));
  }

  @PostMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT.UPDATE')")
  public ResponseEntity<ApiResponse<List<BoqOperationDto>>> split(
      @PathVariable UUID projectId,
      @PathVariable UUID itemId,
      @Valid @RequestBody SplitBoqItemRequest request) {
    log.info("POST /v1/projects/{}/boq/{}/operations - mode={} ops={}",
        projectId, itemId, request.splitMode(),
        request.operations() != null ? request.operations().size() : 0);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(boqOperationService.split(projectId, itemId, request)));
  }

  @PutMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT.UPDATE')")
  public ResponseEntity<ApiResponse<List<BoqOperationDto>>> reweight(
      @PathVariable UUID projectId,
      @PathVariable UUID itemId,
      @Valid @RequestBody SplitBoqItemRequest request) {
    log.info("PUT /v1/projects/{}/boq/{}/operations", projectId, itemId);
    return ResponseEntity.ok(ApiResponse.ok(boqOperationService.reweight(projectId, itemId, request)));
  }

  @DeleteMapping
  @PreAuthorize("@projectAccess.hasProjectPermission(#projectId, 'PROJECT.UPDATE')")
  public ResponseEntity<ApiResponse<Void>> unsplit(
      @PathVariable UUID projectId,
      @PathVariable UUID itemId) {
    log.info("DELETE /v1/projects/{}/boq/{}/operations", projectId, itemId);
    boqOperationService.unsplit(projectId, itemId);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }
}
