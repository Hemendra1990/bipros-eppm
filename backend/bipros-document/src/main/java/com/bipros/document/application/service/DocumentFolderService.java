package com.bipros.document.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.document.application.dto.DocumentFolderRequest;
import com.bipros.document.application.dto.DocumentFolderResponse;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentFolderService {

    private final DocumentFolderRepository folderRepository;

    public DocumentFolderResponse createFolder(UUID projectId, DocumentFolderRequest request) {
        DocumentFolder folder = new DocumentFolder();
        folder.setProjectId(projectId);
        folder.setName(request.name());
        folder.setCode(request.code());
        folder.setCategory(request.category());
        folder.setParentId(request.parentId());
        folder.setWbsNodeId(request.wbsNodeId());
        folder.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        DocumentFolder saved = folderRepository.save(folder);
        return DocumentFolderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DocumentFolderResponse getFolder(UUID projectId, UUID folderId) {
        DocumentFolder folder = folderRepository.findByProjectIdAndId(projectId, folderId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentFolder", folderId));
        return DocumentFolderResponse.from(folder);
    }

    @Transactional(readOnly = true)
    public List<DocumentFolderResponse> listRootFolders(UUID projectId) {
        return folderRepository.findByProjectIdAndParentIdOrderBySortOrder(projectId, null)
            .stream()
            .map(DocumentFolderResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentFolderResponse> listChildFolders(UUID projectId, UUID parentId) {
        return folderRepository.findByProjectIdAndParentIdOrderBySortOrder(projectId, parentId)
            .stream()
            .map(DocumentFolderResponse::from)
            .toList();
    }

    public DocumentFolderResponse updateFolder(UUID projectId, UUID folderId, DocumentFolderRequest request) {
        DocumentFolder folder = folderRepository.findByProjectIdAndId(projectId, folderId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentFolder", folderId));

        folder.setName(request.name());
        folder.setCode(request.code());
        folder.setCategory(request.category());
        folder.setWbsNodeId(request.wbsNodeId());
        folder.setSortOrder(request.sortOrder() != null ? request.sortOrder() : folder.getSortOrder());

        DocumentFolder updated = folderRepository.save(folder);
        return DocumentFolderResponse.from(updated);
    }

    public void deleteFolder(UUID projectId, UUID folderId) {
        DocumentFolder folder = folderRepository.findByProjectIdAndId(projectId, folderId)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentFolder", folderId));
        folderRepository.delete(folder);
    }
}
