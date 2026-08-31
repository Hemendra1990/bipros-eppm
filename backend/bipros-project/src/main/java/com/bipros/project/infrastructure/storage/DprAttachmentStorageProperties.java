package com.bipros.project.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures where DPR photo binaries are persisted on disk. Mirrors the
 * {@code bipros.document.storage} convention so dev, staging, and prod can use the same shared
 * volume layout (or be swapped to S3-backed implementations later).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bipros.dpr.storage")
public class DprAttachmentStorageProperties {

    /** Root directory where DPR photo binaries are stored. Must be writable by the application user. */
    private String path = "./storage/dpr-photos";

    /** Maximum file size allowed per upload in bytes. Defaults to 15 MB. */
    private long maxFileSize = 15L * 1024 * 1024;
}
