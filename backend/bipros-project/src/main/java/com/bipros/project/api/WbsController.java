package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateWbsNodeRequest;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
import com.bipros.project.application.dto.WbsNodeResponse;
import com.bipros.project.application.service.WbsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/projects/{projectId}/wbs")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class WbsController {

    private final WbsService wbsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WbsNodeResponse>>> getTree(@PathVariable UUID projectId) {
        List<WbsNodeResponse> tree = wbsService.getTree(projectId);
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WbsNodeResponse>> getNode(@PathVariable UUID projectId, @PathVariable UUID id) {
        WbsNodeResponse response = wbsService.getNode(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WbsNodeResponse>> createNode(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateWbsNodeRequest request) {
        WbsNodeResponse response = wbsService.createNode(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WbsNodeResponse>> updateNode(
        @PathVariable UUID projectId,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEpsNodeRequest request) {
        WbsNodeResponse response = wbsService.updateNode(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNode(@PathVariable UUID projectId, @PathVariable UUID id) {
        wbsService.deleteNode(id);
        return ResponseEntity.noContent().build();
    }
}
