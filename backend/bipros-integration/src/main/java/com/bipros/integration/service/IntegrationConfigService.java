package com.bipros.integration.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.integration.dto.IntegrationConfigDto;
import com.bipros.integration.model.IntegrationConfig;
import com.bipros.integration.repository.IntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class IntegrationConfigService {

    private final IntegrationConfigRepository integrationConfigRepository;

    public List<IntegrationConfigDto> listAll() {
        return integrationConfigRepository.findAll()
            .stream()
            .map(IntegrationConfigDto::from)
            .toList();
    }

    public IntegrationConfigDto getBySystemCode(String systemCode) {
        return integrationConfigRepository.findBySystemCode(systemCode)
            .map(IntegrationConfigDto::from)
            .orElseThrow(() -> new ResourceNotFoundException("IntegrationConfig", systemCode));
    }

    public IntegrationConfigDto getById(UUID id) {
        return integrationConfigRepository.findById(id)
            .map(IntegrationConfigDto::from)
            .orElseThrow(() -> new ResourceNotFoundException("IntegrationConfig", id.toString()));
    }

    public IntegrationConfigDto create(IntegrationConfigDto dto) {
        if (integrationConfigRepository.findBySystemCode(dto.getSystemCode()).isPresent()) {
            throw new BusinessRuleException(
                "INTEGRATION_CONFIG_DUPLICATE",
                "Integration config already exists for system code: " + dto.getSystemCode()
            );
        }

        IntegrationConfig config = new IntegrationConfig();
        config.setSystemCode(dto.getSystemCode());
        config.setSystemName(dto.getSystemName());
        config.setBaseUrl(dto.getBaseUrl());
        config.setApiKey(dto.getApiKey());
        config.setIsEnabled(dto.getIsEnabled());
        config.setAuthType(dto.getAuthType());
        config.setStatus(dto.getStatus());
        config.setConfigJson(dto.getConfigJson());

        IntegrationConfig saved = integrationConfigRepository.save(config);
        return IntegrationConfigDto.from(saved);
    }

    public IntegrationConfigDto update(UUID id, IntegrationConfigDto dto) {
        IntegrationConfig config = integrationConfigRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("IntegrationConfig", id.toString()));

        config.setBaseUrl(dto.getBaseUrl());
        config.setApiKey(dto.getApiKey());
        config.setIsEnabled(dto.getIsEnabled());
        config.setAuthType(dto.getAuthType());
        config.setStatus(dto.getStatus());
        config.setConfigJson(dto.getConfigJson());

        IntegrationConfig updated = integrationConfigRepository.save(config);
        return IntegrationConfigDto.from(updated);
    }

    public void delete(UUID id) {
        if (!integrationConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("IntegrationConfig", id.toString());
        }
        integrationConfigRepository.deleteById(id);
    }
}
