package com.bipros.contract.infrastructure.storage;

import com.bipros.contract.domain.model.AttachmentEntityType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Filesystem binary storage for contract attachments.
 *
 * <p>Layout on disk:
 * <pre>
 *   {storageRoot}/{projectId}/{contractId}/{entityType}/{entityId}/{attachmentId}/{safeFileName}
 * </pre>
 *
 * <p>The relative path is what {@code ContractAttachment.filePath} stores in the DB so the
 * storage root can move without rewriting rows.
 *
 * TODO(v2): extract a generic {@code FileStorageService} to {@code bipros-common} and have
 *           both this service and {@code DocumentStorageService} consume it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractFileStorageService {

    private final ContractFileStorageProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() {
        try {
            storageRoot = Paths.get(properties.getPath()).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
            log.info("[Contract storage] binaries will be written under {}", storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to initialise contract storage root at " + properties.getPath(), e);
        }
    }

    public StoredFile store(
            UUID projectId,
            UUID contractId,
            AttachmentEntityType entityType,
            UUID entityId,
            UUID attachmentId,
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    "Uploaded file exceeds max allowed size of " + properties.getMaxFileSize() + " bytes");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        String safeFileName = sanitizeFileName(originalFilename);

        Path relative = Paths.get(
                projectId.toString(),
                contractId.toString(),
                entityType.name(),
                entityId.toString(),
                attachmentId.toString(),
                safeFileName);

        Path target = resolveSafely(relative);

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file at " + relative, e);
        }

        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        long size = file.getSize();

        log.info("[Contract storage] stored {} bytes at {} ({})", size, relative, mimeType);

        return new StoredFile(
                relative.toString().replace('\\', '/'),
                safeFileName,
                mimeType,
                size);
    }

    public Resource load(String relativePath) {
        Path target = resolveSafely(Paths.get(relativePath));
        if (!Files.exists(target) || !Files.isReadable(target)) {
            throw new IllegalStateException("File not found or unreadable: " + relativePath);
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists()) {
                throw new IllegalStateException("File not found: " + relativePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid storage path: " + relativePath, e);
        }
    }

    /** Best-effort delete — logs but doesn't fail on I/O errors. */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path target = resolveSafely(Paths.get(relativePath));
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("[Contract storage] failed to delete {}: {}", relativePath, e.getMessage());
        }
    }

    private Path resolveSafely(Path relative) {
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal detected: " + relative);
        }
        return target;
    }

    private static String sanitizeFileName(String originalFilename) {
        String name = Paths.get(originalFilename).getFileName().toString();
        name = name.replaceAll("[\\\\/\\x00]", "_");
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot > name.length() - 12) {
                String ext = name.substring(dot);
                name = name.substring(0, 200 - ext.length()) + ext;
            } else {
                name = name.substring(0, 200);
            }
        }
        return name;
    }

    public record StoredFile(
            String relativePath,
            String fileName,
            String mimeType,
            long fileSize) {
    }
}
