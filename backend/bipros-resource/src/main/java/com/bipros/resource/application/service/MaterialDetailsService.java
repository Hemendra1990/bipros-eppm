package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.MaterialDetailsDto;
import com.bipros.resource.domain.model.ResourceMaterialDetails;
import com.bipros.resource.domain.repository.ResourceMaterialDetailsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialDetailsService {

  private final ResourceMaterialDetailsRepository repository;
  private final ResourceRepository resourceRepository;

  @Transactional(readOnly = true)
  public MaterialDetailsDto get(UUID resourceId) {
    return repository.findById(resourceId)
        .map(MaterialDetailsDto::from)
        .orElse(null);
  }

  public MaterialDetailsDto upsert(UUID resourceId, MaterialDetailsDto dto) {
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    ResourceMaterialDetails m = repository.findById(resourceId)
        .orElseGet(ResourceMaterialDetails::new);
    m.setResourceId(resourceId);
    apply(m, dto);
    return MaterialDetailsDto.from(repository.save(m));
  }

  public void delete(UUID resourceId) {
    repository.deleteById(resourceId);
  }

  static void apply(ResourceMaterialDetails m, MaterialDetailsDto dto) {
    m.setMaterialType(dto.materialType());
    m.setCategory(dto.category());
    m.setSubCategory(dto.subCategory());
    m.setMaterialGrade(dto.materialGrade());
    m.setSpecification(dto.specification());
    m.setBrand(dto.brand());
    m.setManufacturerName(dto.manufacturerName());
    m.setStandardCode(dto.standardCode());
    m.setQualityClass(dto.qualityClass());
    m.setBaseUnit(dto.baseUnit());
    m.setConversionFactor(dto.conversionFactor());
    m.setAlternateUnits(dto.alternateUnits());
    m.setDensity(dto.density());
  }
}
