package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.manpower.ManpowerSkills;

/**
 * {@code primarySkill} now stores a JSON-stringified array of skill names from {@code SkillMaster}
 * (e.g. '["Mason","Welder"]') — multi-select on the form. Legacy single-value strings remain
 * readable. {@code skillLevel} is a plain string now (was enum), populated from
 * {@code SkillLevelMaster}.
 */
public record ManpowerSkillsDto(
    String primarySkill,
    String secondarySkills,
    String skillLevel,
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
