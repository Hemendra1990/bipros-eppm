package com.bipros.ai.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Materialised view of a single Resource for the AI tools. Pulled together by
 * {@link ResourceContextFacade} from {@code Resource}, {@code ResourceRole},
 * {@code ResourceType}, {@code ManpowerMaster}, {@code ManpowerSkills}, and
 * {@code ResourceRate}. Fields are nullable when the source row is absent
 * (a non-manpower resource has no manpower-master row, an inactive resource
 * may have no current rate, etc.).
 */
public record ResourceProfile(
    UUID resourceId,
    String code,
    String name,
    String description,
    String unit,
    String status,
    BigDecimal availability,
    BigDecimal costPerUnit,
    UUID parentId,
    String parentCode,
    String parentName,
    UUID userId,
    UUID calendarId,
    UUID roleId,
    String roleCode,
    String roleName,
    UUID resourceTypeId,
    String resourceTypeCode,
    String resourceTypeName,
    String resourceTypeCategory,
    Manpower manpower,
    Skills skills,
    List<RateSnapshot> rates,
    List<Subordinate> subordinates) {

  public record Manpower(
      String employeeCode,
      String fullName,
      String designation,
      String department,
      String category,
      String subCategory,
      String employmentType,
      String nationality,
      String contactNumber,
      String email,
      LocalDate joiningDate,
      LocalDate exitDate,
      UUID reportingManagerId,
      String reportingManagerName,
      String companyName,
      String workLocation) {}

  public record Skills(
      String primarySkill,
      String secondarySkillsJson,
      String skillLevel,
      String certificationsJson,
      String licenseDetailsJson,
      String trainingRecordsJson,
      Integer experienceYears) {}

  public record RateSnapshot(
      String rateType,
      BigDecimal pricePerUnit,
      BigDecimal budgetedRate,
      BigDecimal actualRate,
      BigDecimal variance,
      LocalDate effectiveDate,
      LocalDate effectiveTo,
      String category) {}

  public record Subordinate(
      UUID resourceId,
      String code,
      String name,
      String roleName,
      String resourceTypeCategory,
      String fullName,
      String designation,
      String linkSource) {}
}
