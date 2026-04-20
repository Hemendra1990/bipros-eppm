package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.document.application.dto.DocumentDownload;
import com.bipros.document.application.dto.DocumentRequest;
import com.bipros.document.application.dto.DocumentResponse;
import com.bipros.document.application.dto.DocumentVersionRequest;
import com.bipros.document.application.dto.DocumentVersionResponse;
import com.bipros.document.application.dto.UploadDocumentRequest;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.model.DocumentVersion;
import com.bipros.document.domain.repository.DocumentRepository;
import com.bipros.document.domain.repository.DocumentVersionRepository;
import com.bipros.document.infrastructure.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final AuditService auditService;
    private final DocumentStorageService storageService;

    public DocumentResponse createDocument(UUID projectId, DocumentRequest request) {
        validateDocumentNumberPrefix(request.documentType(), request.documentNumber());

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
        document.setDocumentType(request.documentType());
        document.setDiscipline(request.discipline());
        document.setTransmittalNumber(request.transmittalNumber());
        document.setWbsPackageCode(request.wbsPackageCode());
        document.setIssuedBy(request.issuedBy());
        document.setIssuedDate(request.issuedDate());
        document.setApprovedBy(request.approvedBy());
        document.setApprovedDate(request.approvedDate());
        document.setTags(request.tags());
        document.setCurrentVersion(1);

        Document saved = documentRepository.save(document);
        auditService.logCreate("Document", saved.getId(), saved);

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

        auditService.logUpdate("Document", documentId, "title", document.getTitle(), request.title());
        auditService.logUpdate("Document", documentId, "status", document.getStatus(), request.status());

        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setStatus(request.status() != null ? request.status() : document.getStatus());
        if (request.documentType() != null) {
            document.setDocumentType(request.documentType());
        }
        if (request.discipline() != null) {
            document.setDiscipline(request.discipline());
        }
        if (request.transmittalNumber() != null) {
            document.setTransmittalNumber(request.transmittalNumber());
        }
        if (request.wbsPackageCode() != null) {
            document.setWbsPackageCode(request.wbsPackageCode());
        }
        if (request.issuedBy() != null) {
            document.setIssuedBy(request.issuedBy());
        }
        if (request.issuedDate() != null) {
            document.setIssuedDate(request.issuedDate());
        }
        if (request.approvedBy() != null) {
            document.setApprovedBy(request.approvedBy());
        }
        if (request.approvedDate() != null) {
            document.setApprovedDate(request.approvedDate());
        }
        document.setTags(request.tags());

        Document updated = documentRepository.save(document);
        return DocumentResponse.from(updated);
    }

    /** IC-PMS M6: enforce that the document number starts with the type-specific prefix. */
    private void validateDocumentNumberPrefix(DocumentType type, String documentNumber) {
        if (type == null || documentNumber == null) {
            return;
        }
        String expected = type.getCodePrefix();
        if (!documentNumber.startsWith(expected)) {
            throw new IllegalArgumentException(
                "Document number '" + documentNumber + "' must start with '" + expected
                    + "' for type " + type);
        }
    }

    public void deleteDocument(UUID projectId, UUID documentId) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        // Delete versions — purge binaries first, then DB rows
        List<DocumentVersion> versions = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        for (DocumentVersion v : versions) {
            storageService.deleteQuietly(v.getFilePath());
        }
        versionRepository.deleteAll(versions);
        storageService.deleteQuietly(document.getFilePath());

        documentRepository.delete(document);
        auditService.logDelete("Document", documentId);
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
        auditService.logCreate("DocumentVersion", saved.getId(), saved);

        // Update document current version
        document.setCurrentVersion(nextVersion);
        document.setFileName(request.fileName());
        document.setFileSize(request.fileSize());
        document.setFilePath(request.filePath());
        documentRepository.save(document);

        return DocumentVersionResponse.from(saved);
    }

    // ------------------------------------------------------------------ file I/O

    /**
     * Uploads a new document with its binary. Writes the file to disk, creates the Document row
     * (fileName / fileSize / mimeType / filePath all derived from the {@link MultipartFile})
     * and records version 1.
     */
    public DocumentResponse uploadDocument(
            UUID projectId,
            UploadDocumentRequest metadata,
            MultipartFile file,
            String uploadedBy) {
        validateDocumentNumberPrefix(metadata.documentType(), metadata.documentNumber());

        // Pre-compute the bits we need for the NOT NULL columns. filePath is filled with
        // a placeholder; it's overwritten after the binary is stored (we need the generated
        // UUID to build the on-disk path).
        String originalFileName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        Document document = new Document();
        document.setProjectId(projectId);
        document.setFolderId(metadata.folderId());
        document.setDocumentNumber(metadata.documentNumber());
        document.setTitle(metadata.title());
        document.setDescription(metadata.description());
        document.setFileName(originalFileName);
        document.setFileSize(file.getSize());
        document.setMimeType(mimeType);
        document.setFilePath("pending");
        document.setStatus(metadata.status() != null ? metadata.status() : document.getStatus());
        document.setDocumentType(metadata.documentType());
        document.setDiscipline(metadata.discipline());
        document.setTransmittalNumber(metadata.transmittalNumber());
        document.setWbsPackageCode(metadata.wbsPackageCode());
        document.setIssuedBy(metadata.issuedBy());
        document.setIssuedDate(metadata.issuedDate());
        document.setApprovedBy(metadata.approvedBy());
        document.setApprovedDate(metadata.approvedDate());
        document.setTags(metadata.tags());
        document.setCurrentVersion(1);

        // Stage 1: insert so JPA assigns the UUID
        Document saved = documentRepository.save(document);

        // Stage 2: write the binary under the generated document ID
        DocumentStorageService.StoredFile stored =
                storageService.store(projectId, saved.getId(), 1, file);

        // Stage 3: back-fill the real file metadata
        saved.setFileName(stored.fileName());
        saved.setFileSize(stored.fileSize());
        saved.setMimeType(stored.mimeType());
        saved.setFilePath(stored.relativePath());
        saved = documentRepository.save(saved);

        auditService.logCreate("Document", saved.getId(), saved);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(saved.getId());
        version.setVersionNumber(1);
        version.setFileName(stored.fileName());
        version.setFilePath(stored.relativePath());
        version.setFileSize(stored.fileSize());
        version.setUploadedBy(uploadedBy != null ? uploadedBy : "SYSTEM");
        version.setUploadedAt(Instant.now());
        versionRepository.save(version);

        return DocumentResponse.from(saved);
    }

    /**
     * Uploads a new version of an existing document — increments currentVersion,
     * stores the binary under {@code v{n}/}, updates Document + creates DocumentVersion row.
     */
    public DocumentVersionResponse uploadVersion(
            UUID projectId,
            UUID documentId,
            MultipartFile file,
            String changeDescription,
            String uploadedBy) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        int nextVersion = document.getCurrentVersion() + 1;
        DocumentStorageService.StoredFile stored = storageService.store(projectId, documentId, nextVersion, file);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNumber(nextVersion);
        version.setFileName(stored.fileName());
        version.setFilePath(stored.relativePath());
        version.setFileSize(stored.fileSize());
        version.setChangeDescription(changeDescription);
        version.setUploadedBy(uploadedBy != null ? uploadedBy : "SYSTEM");
        version.setUploadedAt(Instant.now());
        DocumentVersion savedVersion = versionRepository.save(version);
        auditService.logCreate("DocumentVersion", savedVersion.getId(), savedVersion);

        // Update Document to point at the new current version
        document.setCurrentVersion(nextVersion);
        document.setFileName(stored.fileName());
        document.setFileSize(stored.fileSize());
        document.setMimeType(stored.mimeType());
        document.setFilePath(stored.relativePath());
        documentRepository.save(document);

        return DocumentVersionResponse.from(savedVersion);
    }

    /** Returns the current-version binary ready to stream back to the client. */
    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(UUID projectId, UUID documentId) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            throw new IllegalStateException("Document has no file attached: " + documentId);
        }
        return new DocumentDownload(
                storageService.load(document.getFilePath()),
                document.getFileName(),
                document.getMimeType(),
                document.getFileSize());
    }

    /** Returns a specific historical version binary. */
    @Transactional(readOnly = true)
    public DocumentDownload downloadVersion(UUID projectId, UUID documentId, Integer versionNumber) {
        Document document = documentRepository.findByProjectIdAndId(projectId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        DocumentVersion version = versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .filter(v -> v.getVersionNumber().equals(versionNumber))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DocumentVersion", documentId + "/v" + versionNumber));
        return new DocumentDownload(
                storageService.load(version.getFilePath()),
                version.getFileName(),
                document.getMimeType(),
                version.getFileSize());
    }
}
