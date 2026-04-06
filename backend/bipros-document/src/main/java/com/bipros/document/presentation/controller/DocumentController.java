package com.bipros.document.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.document.application.dto.DocumentRequest;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.dto.DocumentVersionRequest;
import com.bipros.document.application.dto.DocumentVersionResponse;
import com.bipros.document.application.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/v1/projects/{projectId}/documents")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
        @PathVariable UUID projectId,
        @Valid @RequestBody DocumentRequest request) {
        DocumentResponse response = documentService.createDocument(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(response));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId) {
        DocumentResponse response = documentService.getDocument(projectId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> listDocuments(
        @PathVariable UUID projectId) {
        List<DocumentResponse> response = documentService.listDocuments(projectId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateDocument(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @Valid @RequestBody DocumentRequest request) {
        DocumentResponse response = documentService.updateDocument(projectId, documentId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId) {
        documentService.deleteDocument(projectId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{documentId}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersionResponse>>> getVersions(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId) {
        List<DocumentVersionResponse> response = documentService.getDocumentVersions(projectId, documentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{documentId}/versions")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> addVersion(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @Valid @RequestBody DocumentVersionRequest request) {
        String uploadedBy = "CURRENT_USER";
        DocumentVersionResponse response = documentService.addVersion(projectId, documentId, request, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(response));
    }
}
