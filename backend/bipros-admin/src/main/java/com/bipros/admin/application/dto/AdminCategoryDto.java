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
public class AdminCategoryDto {
    private UUID id;
    private String categoryType;
    private String code;
    private String name;
    private UUID parentId;
    private int sortOrder;
}
