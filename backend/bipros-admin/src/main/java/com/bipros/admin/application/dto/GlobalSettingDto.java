package com.bipros.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSettingDto {
    private UUID id;
    private String settingKey;
    private String settingValue;
    private String description;
    private String category;
}
