package com.bipros.admin.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.admin.application.dto.CreateGlobalSettingRequest;
import com.bipros.admin.application.dto.GlobalSettingDto;
import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GlobalSettingService {

    private final GlobalSettingRepository globalSettingRepository;
    private final AuditService auditService;

    public GlobalSettingDto createSetting(CreateGlobalSettingRequest request) {
        GlobalSetting setting = new GlobalSetting();
        setting.setSettingKey(request.getSettingKey());
        setting.setSettingValue(request.getSettingValue());
        setting.setDescription(request.getDescription());
        setting.setCategory(request.getCategory());

        GlobalSetting saved = globalSettingRepository.save(setting);
        auditService.logCreate("GlobalSetting", saved.getId(), mapToDto(saved));
        return mapToDto(saved);
    }

    public GlobalSettingDto updateSetting(UUID id, CreateGlobalSettingRequest request) {
        GlobalSetting setting = globalSettingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GlobalSetting", id));

        setting.setSettingKey(request.getSettingKey());
        setting.setSettingValue(request.getSettingValue());
        setting.setDescription(request.getDescription());
        setting.setCategory(request.getCategory());

        GlobalSetting updated = globalSettingRepository.save(setting);
        auditService.logUpdate("GlobalSetting", id, "setting", null, mapToDto(updated));
        return mapToDto(updated);
    }

    public void deleteSetting(UUID id) {
        GlobalSetting setting = globalSettingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GlobalSetting", id));
        globalSettingRepository.delete(setting);
        auditService.logDelete("GlobalSetting", id);
    }

    @Transactional(readOnly = true)
    public GlobalSettingDto getSetting(UUID id) {
        GlobalSetting setting = globalSettingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GlobalSetting", id));
        return mapToDto(setting);
    }

    @Transactional(readOnly = true)
    public GlobalSettingDto getSettingByKey(String key) {
        GlobalSetting setting = globalSettingRepository.findBySettingKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("GlobalSetting", key));
        return mapToDto(setting);
    }

    @Transactional(readOnly = true)
    public List<GlobalSettingDto> getSettingsByCategory(String category) {
        return globalSettingRepository.findByCategory(category).stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GlobalSettingDto> getAllSettings() {
        return globalSettingRepository.findAll().stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    public void setSetting(String key, String value) {
        GlobalSetting setting = globalSettingRepository.findBySettingKey(key)
            .orElse(new GlobalSetting());

        setting.setSettingKey(key);
        setting.setSettingValue(value);
        GlobalSetting saved = globalSettingRepository.save(setting);
        auditService.logCreate("GlobalSetting", saved.getId(), mapToDto(saved));
    }

    private GlobalSettingDto mapToDto(GlobalSetting setting) {
        return GlobalSettingDto.builder()
            .id(setting.getId())
            .settingKey(setting.getSettingKey())
            .settingValue(setting.getSettingValue())
            .description(setting.getDescription())
            .category(setting.getCategory())
            .build();
    }
}
