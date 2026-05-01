package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.model.ResourceOwnership;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EquipmentDetailsDto(
    String make,
    String model,
    String variant,
    String manufacturerName,
    String countryOfOrigin,
    Integer yearOfManufacture,
    String serialNumber,
    String chassisNumber,
    String engineNumber,
    String registrationNumber,
    String capacitySpec,
    BigDecimal fuelLitresPerHour,
    BigDecimal standardOutputPerDay,
    String standardOutputUnit,
    ResourceOwnership ownershipType,
    Integer quantityAvailable,
    LocalDate insuranceExpiry,
    LocalDate lastServiceDate,
    LocalDate nextServiceDate
) {

  public static EquipmentDetailsDto from(ResourceEquipmentDetails e) {
    if (e == null) return null;
    return new EquipmentDetailsDto(
        e.getMake(),
        e.getModel(),
        e.getVariant(),
        e.getManufacturerName(),
        e.getCountryOfOrigin(),
        e.getYearOfManufacture(),
        e.getSerialNumber(),
        e.getChassisNumber(),
        e.getEngineNumber(),
        e.getRegistrationNumber(),
        e.getCapacitySpec(),
        e.getFuelLitresPerHour(),
        e.getStandardOutputPerDay(),
        e.getStandardOutputUnit(),
        e.getOwnershipType(),
        e.getQuantityAvailable(),
        e.getInsuranceExpiry(),
        e.getLastServiceDate(),
        e.getNextServiceDate());
  }
}
