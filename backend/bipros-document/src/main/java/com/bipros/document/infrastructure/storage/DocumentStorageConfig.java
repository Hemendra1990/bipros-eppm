package com.bipros.document.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link DocumentStorageProperties} as a bean so Spring binds
 * {@code bipros.document.storage.*} configuration values to it.
 */
@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class DocumentStorageConfig {
}
