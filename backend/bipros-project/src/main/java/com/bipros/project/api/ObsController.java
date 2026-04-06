package com.bipros.project.api;

import com.bipros.common.dto.ApiResponse;
import com.bipros.project.application.dto.CreateEpsNodeRequest;
import com.bipros.project.application.dto.EpsNodeResponse;
import com.bipros.project.application.dto.UpdateEpsNodeRequest;
import com.bipros.project.application.service.ObsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/obs")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class ObsController {

    private final ObsService obsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EpsNodeResponse>>> getTree() {
        List<EpsNodeResponse> tree = obsService.getTree();
        return ResponseEntity.ok(ApiResponse.ok(tree));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EpsNodeResponse>> getNode(@PathVariable UUID id) {
        EpsNodeResponse response = obsService.getNode(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EpsNodeResponse>> createNode(@Valid @RequestBody CreateEpsNodeRequest request) {
        EpsNodeResponse response = obsService.createNode(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EpsNodeResponse>> updateNode(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEpsNodeRequest request) {
        EpsNodeResponse response = obsService.updateNode(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNode(@PathVariable UUID id) {
        obsService.deleteNode(id);
        return ResponseEntity.noContent().build();
    }
}
