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
public class UnitOfMeasureDto {
    private UUID id;
    private String code;
    private String name;
    private String abbreviation;
    private String category;
}
