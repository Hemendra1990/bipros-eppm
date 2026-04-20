package com.bipros.document.infrastructure.storage;

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
 * Binary storage for document files. Writes to a configured filesystem root, enforcing
 * path-traversal safety and namespacing binaries by project/document/version.
 *
 * <p>Layout on disk:
 * <pre>
 *   {storageRoot}/{projectId}/{documentId}/v{versionNumber}/{safeFileName}
 * </pre>
 *
 * <p>The <em>relative</em> path (everything after {storageRoot}) is what {@code Document.filePath}
 * and {@code DocumentVersion.filePath} store in the database — that way the storage root can move
 * without rewriting rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final DocumentStorageProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() {
        try {
            storageRoot = Paths.get(properties.getPath()).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
            log.info("[Document storage] binaries will be written under {}", storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to initialise document storage root at " + properties.getPath(), e);
        }
    }

    /**
     * Persists {@code file} for a specific project/document/version combination.
     *
     * @return the <em>relative</em> path (from storage root) that should be saved in the DB.
     */
    public StoredFile store(UUID projectId, UUID documentId, int versionNumber, MultipartFile file) {
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
                documentId.toString(),
                "v" + versionNumber,
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

        log.info("[Document storage] stored {} bytes at {} ({})", size, relative, mimeType);

        return new StoredFile(
                relative.toString().replace('\\', '/'),
                safeFileName,
                mimeType,
                size);
    }

    /**
     * Returns a readable {@link Resource} for the given relative path (as stored in the DB).
     */
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
            log.warn("[Document storage] failed to delete {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * Resolves {@code relative} against {@link #storageRoot}, verifying the canonical result
     * is still inside the root. Guards against {@code ..} and absolute-path traversal.
     */
    private Path resolveSafely(Path relative) {
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal detected: " + relative);
        }
        return target;
    }

    /** Strips path separators and trims to a sane length; keeps extension. */
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

    /** Metadata returned to the service layer after a successful write. */
    public record StoredFile(
            String relativePath,
            String fileName,
            String mimeType,
            long fileSize) {
    }
}
