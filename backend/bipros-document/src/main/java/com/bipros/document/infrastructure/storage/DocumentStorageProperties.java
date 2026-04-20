package com.bipros.document.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures where document binaries are persisted on disk.
 *
 * <p>Default is {@code ./storage/documents} relative to the working directory so dev works
 * out-of-the-box. In production this should be pointed at a shared volume (EFS/NFS) or the
 * service should be replaced with an S3-backed implementation.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bipros.document.storage")
public class DocumentStorageProperties {

    /** Root directory where binaries are stored. Must be writable by the application user. */
    private String path = "./storage/documents";

    /** Maximum file size allowed per upload in bytes. Defaults to 50 MB. */
    private long maxFileSize = 50L * 1024 * 1024;
}
