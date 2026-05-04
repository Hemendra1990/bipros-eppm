package com.bipros.resource.domain.model.master;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Master for Manpower Skill Level — Apprentice / Beginner / Intermediate / Expert / Master /
 * admin-defined. Stored on {@code ManpowerSkills.skillLevel} as the master row's {@code name}
 * (string), keeping the existing column.
 */
@Entity
@Table(
    name = "skill_level_master",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_skill_level_master_code", columnNames = {"code"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillLevelMaster extends BaseEntity {

  @Column(nullable = false, length = 50, unique = true)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "sort_order", nullable = false)
  @Default
  private Integer sortOrder = 0;

  @Column(nullable = false)
  @Default
  private Boolean active = true;
}
