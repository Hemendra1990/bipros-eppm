package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.enums.SkillLevel;
import com.bipros.resource.domain.model.manpower.ManpowerSkills;

public record ManpowerSkillsDto(
    String primarySkill,
    String secondarySkills,
    SkillLevel skillLevel,
    String certifications,
    String licenseDetails,
    Integer experienceYears,
    String trainingRecords
) {

  public static ManpowerSkillsDto from(ManpowerSkills s) {
    if (s == null) return null;
    return new ManpowerSkillsDto(
        s.getPrimarySkill(),
        s.getSecondarySkills(),
        s.getSkillLevel(),
        s.getCertifications(),
        s.getLicenseDetails(),
        s.getExperienceYears(),
        s.getTrainingRecords());
  }
}
