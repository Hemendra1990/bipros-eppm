package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceMaterialDetails;
import com.bipros.resource.domain.model.enums.MaterialType;

import java.math.BigDecimal;

public record MaterialDetailsDto(
    MaterialType materialType,
    String category,
    String subCategory,
    String materialGrade,
    String specification,
    String brand,
    String manufacturerName,
    String standardCode,
    String qualityClass,
    String baseUnit,
    BigDecimal conversionFactor,
    String alternateUnits,
    BigDecimal density
) {

  public static MaterialDetailsDto from(ResourceMaterialDetails m) {
    if (m == null) return null;
    return new MaterialDetailsDto(
        m.getMaterialType(),
        m.getCategory(),
        m.getSubCategory(),
        m.getMaterialGrade(),
        m.getSpecification(),
        m.getBrand(),
        m.getManufacturerName(),
        m.getStandardCode(),
        m.getQualityClass(),
        m.getBaseUnit(),
        m.getConversionFactor(),
        m.getAlternateUnits(),
        m.getDensity());
  }
}
