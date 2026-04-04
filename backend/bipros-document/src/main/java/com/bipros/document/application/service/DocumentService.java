package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.document.application.dto.DocumentRequest;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.dto.DocumentVersionRequest;
import com.bipros.document.application.dto.DocumentVersionResponse;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentVersion;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;

    public DocumentResponse createDocument(UUID projectId, DocumentRequest request) {
        Document document = new Document();
        document.setProjectId(projectId);
        document.setFolderId(request.folderId());
        document.setDocumentNumber(request.documentNumber());
        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setFileName(request.fileName());
        document.setFileSize(request.fileSize());
        document.setMimeType(request.mimeType());
        document.setFilePath(request.filePath());
        document.setStatus(request.status() != null ? request.status() : document.getStatus());
        document.setTags(request.tags());
        document.setCurrentVersion(1);

        Document saved = documentRepository.save(document);

        // Create initial version
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(saved.getId());
        version.setVersionNumber(1);
        version.setFileName(request.fileName());
        version.setFilePath(request.filePath());
        version.setFileSize(request.fileSize());
        version.setUploadedBy("SYSTEM");
        version.setUploadedAt(Instant.now());
        versionRepository.save(version);

        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID projectId, UUID documentId) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID projectId) {
        return documentRepository.findByProjectId(projectId)
            .stream()
            .map(DocumentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocumentsByFolder(UUID folderId) {
        return documentRepository.findByFolderId(folderId)
            .stream()
            .map(DocumentResponse::from)
            .toList();
    }

    public DocumentResponse updateDocument(UUID projectId, UUID documentId, DocumentRequest request) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setStatus(request.status() != null ? request.status() : document.getStatus());
        document.setTags(request.tags());

        Document updated = documentRepository.save(document);
        return DocumentResponse.from(updated);
    }

    public void deleteDocument(UUID projectId, UUID documentId) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        // Delete versions
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        versionRepository.deleteAll(versions);

        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getDocumentVersions(UUID projectId, UUID documentId) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)
            .stream()
            .map(DocumentVersionResponse::from)
            .toList();
    }

    public DocumentVersionResponse addVersion(UUID projectId, UUID documentId, DocumentVersionRequest request, String uploadedBy) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        int nextVersion = document.getCurrentVersion() + 1;

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(nextVersion);
        version.setFileName(request.fileName());
        version.setFilePath(request.filePath());
        version.setFileSize(request.fileSize());
        version.setChangeDescription(request.changeDescription());
        version.setUploadedBy(uploadedBy);
        version.setUploadedAt(Instant.now());

        DocumentVersion saved = versionRepository.save(version);

        // Update document current version
        document.setCurrentVersion(nextVersion);
        document.setFileName(request.fileName());
        document.setFileSize(request.fileSize());
        document.setFilePath(request.filePath());
        documentRepository.save(document);

        return DocumentVersionResponse.from(saved);
    }
}
