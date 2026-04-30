package com.bipros.ai.image;

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
 * Stores images uploaded to AI chat for vision analysis.
 * Files are written under {storageRoot}/ai-images/{conversationId}/.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiImageStorageService {

    private final com.bipros.document.infrastructure.storage.DocumentStorageProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() {
        try {
            storageRoot = Paths.get(properties.getPath()).toAbsolutePath().normalize().resolve("ai-images");
            Files.createDirectories(storageRoot);
            log.info("[AI image storage] images will be written under {}", storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialise AI image storage", e);
        }
    }

    public StoredImage store(UUID conversationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image is empty");
        }
        long maxSize = 10 * 1024 * 1024; // 10 MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Image exceeds 10 MB limit");
        }

        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image.bin" : file.getOriginalFilename());
        String ext = getImageExtension(original);
        String safeName = UUID.randomUUID() + ext;

        Path relative = Paths.get(conversationId.toString(), safeName);
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal detected");
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store image", e);
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        log.info("[AI image storage] stored {} bytes at {} ({})", file.getSize(), relative, mimeType);

        return new StoredImage(
                relative.toString().replace('\\', '/'),
                safeName,
                mimeType,
                file.getSize()
        );
    }

    public Resource load(String relativePath) {
        Path target = storageRoot.resolve(Paths.get(relativePath)).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Path traversal detected");
        }
        if (!Files.exists(target) || !Files.isReadable(target)) {
            throw new IllegalStateException("Image not found: " + relativePath);
        }
        try {
            return new UrlResource(target.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid image path", e);
        }
    }

    private String getImageExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            String ext = filename.substring(dot).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
                return ext;
            }
        }
        return ".jpg";
    }

    public record StoredImage(String relativePath, String fileName, String mimeType, long fileSize) {
    }
}
