package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.DashboardConfig;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DashboardConfigDto {
    private UUID id;
    private String tier;
    private String name;
    private String layoutConfig;
    private Boolean isDefault;

    public static DashboardConfigDto from(DashboardConfig entity) {
        return DashboardConfigDto.builder()
                .id(entity.getId())
                .tier(entity.getTier().toString())
                .name(entity.getName())
                .layoutConfig(entity.getLayoutConfig())
                .isDefault(entity.getIsDefault())
                .build();
    }
}
