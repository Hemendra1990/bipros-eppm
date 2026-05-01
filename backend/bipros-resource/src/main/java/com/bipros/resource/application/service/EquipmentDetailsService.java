package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.resource.application.dto.EquipmentDetailsDto;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.repository.ResourceEquipmentDetailsRepository;
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
public class EquipmentDetailsService {

  private final ResourceEquipmentDetailsRepository repository;
  private final ResourceRepository resourceRepository;

  @Transactional(readOnly = true)
  public EquipmentDetailsDto get(UUID resourceId) {
    return repository.findById(resourceId)
        .map(EquipmentDetailsDto::from)
        .orElse(null);
  }

  public EquipmentDetailsDto upsert(UUID resourceId, EquipmentDetailsDto dto) {
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    ResourceEquipmentDetails e = repository.findById(resourceId)
        .orElseGet(ResourceEquipmentDetails::new);
    e.setResourceId(resourceId);
    apply(e, dto);
    return EquipmentDetailsDto.from(repository.save(e));
  }

  public void delete(UUID resourceId) {
    repository.deleteById(resourceId);
  }

  static void apply(ResourceEquipmentDetails e, EquipmentDetailsDto dto) {
    e.setMake(dto.make());
    e.setModel(dto.model());
    e.setVariant(dto.variant());
    e.setManufacturerName(dto.manufacturerName());
    e.setCountryOfOrigin(dto.countryOfOrigin());
    e.setYearOfManufacture(dto.yearOfManufacture());
    e.setSerialNumber(dto.serialNumber());
    e.setChassisNumber(dto.chassisNumber());
    e.setEngineNumber(dto.engineNumber());
    e.setRegistrationNumber(dto.registrationNumber());
    e.setCapacitySpec(dto.capacitySpec());
    e.setFuelLitresPerHour(dto.fuelLitresPerHour());
    e.setStandardOutputPerDay(dto.standardOutputPerDay());
    e.setStandardOutputUnit(dto.standardOutputUnit());
    e.setOwnershipType(dto.ownershipType());
    e.setQuantityAvailable(dto.quantityAvailable());
    e.setInsuranceExpiry(dto.insuranceExpiry());
    e.setLastServiceDate(dto.lastServiceDate());
    e.setNextServiceDate(dto.nextServiceDate());
  }
}
