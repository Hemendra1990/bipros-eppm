package com.bipros.integration.storage;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem fallback used by tests and for the no-docker dev flow. Active
 * when {@code bipros.storage.kind=local}. Writes under
 * {@code bipros.storage.local.root} (default {@code ./data/satellite}).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "bipros.storage.kind", havingValue = "local")
public class LocalFilesystemRasterStorage implements RasterStorage {

    private final Path root;

    public LocalFilesystemRasterStorage(
        @Value("${bipros.storage.local.root:./data/satellite}") String rootPath
    ) {
        this.root = Paths.get(rootPath).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BusinessRuleException("STORAGE_INIT",
                "Failed to create local storage root " + root + ": " + e.getMessage());
        }
    }

    @Override
    public URI put(String key, byte[] bytes, String contentType) {
        Path file = root.resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new BusinessRuleException("STORAGE_WRITE", e.getMessage());
        }
        return file.toUri();
    }

    @Override
    public byte[] get(URI uri) {
        Path file = Paths.get(uri);
        if (!Files.exists(file)) {
            throw new BusinessRuleException("STORAGE_NOT_FOUND", "Missing: " + uri);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BusinessRuleException("STORAGE_READ", e.getMessage());
        }
    }

    @Override
    public void delete(URI uri) {
        Path file = Paths.get(uri);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[Storage] delete failed for {}: {}", uri, e.getMessage());
        }
    }
}
