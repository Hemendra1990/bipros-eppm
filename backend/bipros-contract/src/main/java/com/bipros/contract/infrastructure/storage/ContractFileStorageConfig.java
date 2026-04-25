package com.bipros.contract.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ContractFileStorageProperties.class)
public class ContractFileStorageConfig {
}
