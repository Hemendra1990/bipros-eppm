package com.bipros.project.infrastructure.storage;

import com.bipros.common.exception.BusinessRuleException;
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
import java.util.Set;
import java.util.UUID;

/**
 * Binary storage for DPR photo attachments. Files are written under
 * {@code {storageRoot}/{dprId}/{uuid}.{ext}}; the relative path (everything after
 * {@code storageRoot}) is what {@code DprAttachment.storagePath} stores in the database, so the
 * storage root can move without rewriting rows.
 *
 * <p>Only common image MIME types are accepted. Path traversal is rejected at write and read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DprAttachmentStorageService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif"
    );

    private final DprAttachmentStorageProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() {
        try {
            storageRoot = Paths.get(properties.getPath()).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
            log.info("[DPR photo storage] binaries will be written under {}", storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to initialise DPR photo storage at " + properties.getPath(), e);
        }
    }

    public StoredPhoto store(UUID dprId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("DPR_PHOTO_EMPTY", "Uploaded photo is empty");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessRuleException(
                    "DPR_PHOTO_TOO_LARGE",
                    "Photo exceeds max allowed size of " + properties.getMaxFileSize() + " bytes");
        }

        String mimeType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessRuleException(
                    "DPR_PHOTO_UNSUPPORTED_TYPE",
                    "Unsupported image MIME type: " + mimeType);
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename());
        String ext = extensionFor(original, mimeType);
        String safeName = UUID.randomUUID() + ext;

        Path relative = Paths.get(dprId.toString(), safeName);
        Path target = resolveSafely(relative);

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store DPR photo at " + relative, e);
        }

        log.info("[DPR photo storage] stored {} bytes at {} ({})", file.getSize(), relative, mimeType);

        return new StoredPhoto(
                relative.toString().replace('\\', '/'),
                sanitizeFileName(original),
                mimeType,
                file.getSize());
    }

    public Resource load(String relativePath) {
        Path target = resolveSafely(Paths.get(relativePath));
        if (!Files.exists(target) || !Files.isReadable(target)) {
            throw new IllegalStateException("Photo not found or unreadable: " + relativePath);
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists()) {
                throw new IllegalStateException("Photo not found: " + relativePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid storage path: " + relativePath, e);
        }
    }

    /** Best-effort delete — logs but does not fail on I/O errors so DB rollback isn't blocked. */
    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path target = resolveSafely(Paths.get(relativePath));
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("[DPR photo storage] failed to delete {}: {}", relativePath, e.getMessage());
        }
    }

    private Path resolveSafely(Path relative) {
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal detected: " + relative);
        }
        return target;
    }

    private static String extensionFor(String filename, String mimeType) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            String ext = filename.substring(dot).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|webp|heic|heif)")) {
                return ext;
            }
        }
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif" -> ".heic";
            default -> ".jpg";
        };
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

    public record StoredPhoto(
            String relativePath,
            String fileName,
            String mimeType,
            long fileSize) {
    }
}
