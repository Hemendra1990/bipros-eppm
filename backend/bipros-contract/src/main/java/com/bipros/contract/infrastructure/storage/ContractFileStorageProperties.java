package com.bipros.contract.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures where contract attachment binaries are persisted on disk.
 * Default is {@code ./storage/contracts} relative to the working directory.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bipros.contract.storage")
public class ContractFileStorageProperties {

    private String path = "./storage/contracts";

    /** Max upload size in bytes. Defaults to 50 MB to align with Spring's {@code spring.servlet.multipart.max-file-size}. */
    private long maxFileSize = 50L * 1024 * 1024;
}
