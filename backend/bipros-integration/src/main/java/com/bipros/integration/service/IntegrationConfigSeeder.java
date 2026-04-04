package com.bipros.integration.service;

import com.bipros.integration.model.IntegrationConfig;
import com.bipros.integration.repository.IntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationConfigSeeder {

    private final IntegrationConfigRepository integrationConfigRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIntegrationConfigs() {
        seedPfmsConfig();
        seedGemConfig();
        seedCpppConfig();
        seedGstnConfig();
        seedPariveshConfig();
    }

    private void seedPfmsConfig() {
        if (integrationConfigRepository.findBySystemCode("PFMS").isPresent()) {
            log.info("PFMS integration config already exists, skipping seed");
            return;
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode("PFMS");
        config.setSystemName("Public Financial Management System");
        config.setBaseUrl("https://pfms.nic.in/api/v1");
        config.setAuthType(IntegrationConfig.AuthType.API_KEY);
        config.setIsEnabled(false);
        config.setStatus(IntegrationConfig.IntegrationStatus.INACTIVE);
        config.setConfigJson("{\"timeout\": 30000, \"maxRetries\": 3}");

        integrationConfigRepository.save(config);
        log.info("PFMS integration config seeded");
    }

    private void seedGemConfig() {
        if (integrationConfigRepository.findBySystemCode("GEM").isPresent()) {
            log.info("GeM integration config already exists, skipping seed");
            return;
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode("GEM");
        config.setSystemName("Government e-Marketplace");
        config.setBaseUrl("https://api.gem.gov.in/v1");
        config.setAuthType(IntegrationConfig.AuthType.API_KEY);
        config.setIsEnabled(false);
        config.setStatus(IntegrationConfig.IntegrationStatus.INACTIVE);
        config.setConfigJson("{\"timeout\": 30000, \"maxRetries\": 3}");

        integrationConfigRepository.save(config);
        log.info("GeM integration config seeded");
    }

    private void seedCpppConfig() {
        if (integrationConfigRepository.findBySystemCode("CPPP").isPresent()) {
            log.info("CPPP integration config already exists, skipping seed");
            return;
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode("CPPP");
        config.setSystemName("Central Public Procurement Portal");
        config.setBaseUrl("https://cppp.gov.in/api/v1");
        config.setAuthType(IntegrationConfig.AuthType.API_KEY);
        config.setIsEnabled(false);
        config.setStatus(IntegrationConfig.IntegrationStatus.INACTIVE);
        config.setConfigJson("{\"timeout\": 30000, \"maxRetries\": 3}");

        integrationConfigRepository.save(config);
        log.info("CPPP integration config seeded");
    }

    private void seedGstnConfig() {
        if (integrationConfigRepository.findBySystemCode("GSTN").isPresent()) {
            log.info("GSTN integration config already exists, skipping seed");
            return;
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode("GSTN");
        config.setSystemName("Goods and Services Tax Network");
        config.setBaseUrl("https://api.gstn.org/v2");
        config.setAuthType(IntegrationConfig.AuthType.OAUTH2);
        config.setIsEnabled(false);
        config.setStatus(IntegrationConfig.IntegrationStatus.INACTIVE);
        config.setConfigJson("{\"timeout\": 30000, \"maxRetries\": 3}");

        integrationConfigRepository.save(config);
        log.info("GSTN integration config seeded");
    }

    private void seedPariveshConfig() {
        if (integrationConfigRepository.findBySystemCode("PARIVESH").isPresent()) {
            log.info("PARIVESH integration config already exists, skipping seed");
            return;
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode("PARIVESH");
        config.setSystemName("Pro-Active and Reactive Investment Facilitation Engine");
        config.setBaseUrl("https://parivesh.nic.in/api/v1");
        config.setAuthType(IntegrationConfig.AuthType.API_KEY);
        config.setIsEnabled(false);
        config.setStatus(IntegrationConfig.IntegrationStatus.INACTIVE);
        config.setConfigJson("{\"timeout\": 30000, \"maxRetries\": 3}");

        integrationConfigRepository.save(config);
        log.info("PARIVESH integration config seeded");
    }
}
