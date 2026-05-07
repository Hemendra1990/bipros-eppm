package com.bipros.project.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link DprAttachmentStorageProperties} as a bean so Spring binds
 * {@code bipros.dpr.storage.*} into it on startup.
 */
@Configuration
@EnableConfigurationProperties(DprAttachmentStorageProperties.class)
public class DprAttachmentStorageConfig {
}
