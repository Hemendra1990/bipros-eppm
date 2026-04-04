package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.KpiDefinition;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class KpiDefinitionDto {
    private UUID id;
    private String name;
    private String code;
    private String formula;
    private String unit;
    private Double greenThreshold;
    private Double amberThreshold;
    private Double redThreshold;
    private String moduleSource;
    private Boolean isActive;

    public static KpiDefinitionDto from(KpiDefinition entity) {
        return KpiDefinitionDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .formula(entity.getFormula())
                .unit(entity.getUnit())
                .greenThreshold(entity.getGreenThreshold())
                .amberThreshold(entity.getAmberThreshold())
                .redThreshold(entity.getRedThreshold())
                .moduleSource(entity.getModuleSource())
                .isActive(entity.getIsActive())
                .build();
    }
}
